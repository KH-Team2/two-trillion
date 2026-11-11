"""
    카드 파일 해석(parser.py) 테스트
    (실행: 터미널에서 calc 폴더로 경로 잡고 `pytest tests/test_card_parser.py`)

    더미 파일 3개 (tests/fixtures/ 폴더)
    - card_a.xlsx      : 가상 카드사 · 위 3줄 안내 문구 · 사용구분 칸 있음 · 취소 음수
    - card_b.csv       : 가상 카드사 · cp949 · 금액 쉼표 · 사용구분 칸 없음
    - hometax_cash.xls : 홈택스 현금영수증 실제 양식 칸 이름 · 취소거래 1건
"""
from datetime import date
from pathlib import Path

# pytest: 테스트 도구 · pytest.raises(오류) = "이 안에서 그 오류가 나야 통과"
import pytest

from app.card import parser
from app.card.parser import (
    CardParseError,
    find_header,
    parse_file,
    read_rows,
    to_amount,
    to_date,
)

# 이 테스트 파일이 있는 폴더 / fixtures
FIXTURES = Path(__file__).parent / "fixtures"


def load(name):
    """fixtures 폴더의 파일을 (바이트, 파일 이름)으로 읽기"""
    return (FIXTURES / name).read_bytes(), name


# ── 1. read_rows: 파일 읽기 ──
def test_read_csv_utf8():
    rows = read_rows("승인일시,가맹점\n2026-01-01,가게\n".encode("utf-8"), "a.csv")
    assert rows == [["승인일시", "가맹점"], ["2026-01-01", "가게"]]


def test_read_csv_cp949():
    """utf-8로 못 읽으면 cp949로 다시 읽어요"""
    rows = read_rows("승인일시,가맹점\n".encode("cp949"), "a.csv")
    assert rows[0] == ["승인일시", "가맹점"]


def test_read_unsupported_extension():
    with pytest.raises(CardParseError) as error:
        read_rows(b"hello", "a.txt")
    assert error.value.code == "UNSUPPORTED_FORMAT"


def test_read_xls():
    """옛날 엑셀(xls)도 읽혀요 (python-calamine 필요)"""
    rows = read_rows(*load("hometax_cash.xls"))
    assert rows[1][0] == "거래일시"


# ── 2. find_header: 머리글 찾기 ──
def test_find_header_skips_title_lines():
    """card_a.xlsx는 위 3줄이 안내 문구 → 4번째 줄(0부터 세면 3)이 머리글"""
    header_index, columns = find_header(read_rows(*load("card_a.xlsx")))
    assert header_index == 3
    assert columns["merchantName"] == 3
    assert "payMethod" in columns


def test_find_header_not_found():
    with pytest.raises(CardParseError) as error:
        find_header([["날짜", "상점", "돈"]])
    assert error.value.code == "UNSUPPORTED_FORMAT"


# ── 3. to_date · to_amount: 값 바꾸기 ──
@pytest.mark.parametrize("text", ["2026-03-02 08:12:33", "2026.03.02 08:30", "20260302"])
def test_to_date_formats(text):
    """
        여러 날짜 모양 → 전부 2026-03-02 (시간은 버림)
        parametrize: 같은 테스트를 값만 바꿔 여러 번 실행
    """
    assert to_date(text) == date(2026, 3, 2)


@pytest.mark.parametrize("text", ["어제", "2026-02-30", ""])
def test_to_date_invalid(text):
    with pytest.raises(ValueError):
        to_date(text)


@pytest.mark.parametrize(
    "text, expected",
    [("1,450", 1450), ("-54,300", -54300), ("12000", 12000), ("12000.0", 12000), ("3,200원", 3200)],
)
def test_to_amount_formats(text, expected):
    assert to_amount(text) == expected


@pytest.mark.parametrize("text", ["만원", "12.5", ""])
def test_to_amount_invalid(text):
    with pytest.raises(ValueError):
        to_amount(text)


# ── 5. parse_file: 전체 해석 ──
def test_parse_card_a():
    """사용구분 칸이 있는 카드사 파일"""
    result = parse_file(*load("card_a.xlsx"))
    assert len(result) == 14
    assert result[0] == {
        "rowNo": 5,  # 엑셀에서 보이는 5번째 줄 (머리글이 4번째 줄)
        "usedDate": "2026-03-02",
        "merchantName": "서울교통공사",
        "payMethod": "CREDIT",
        "amount": 1550,
    }
    assert result[2]["payMethod"] == "CHECK"  # "체크"
    assert result[11]["amount"] == -32900  # 취소 건은 음수 그대로


def test_parse_card_b_needs_pay_method():
    """사용구분 칸이 없고 사용자가 고르지도 않았으면 PAY_METHOD_REQUIRED"""
    with pytest.raises(CardParseError) as error:
        parse_file(*load("card_b.csv"))
    assert error.value.code == "PAY_METHOD_REQUIRED"


def test_parse_card_b_with_pay_method():
    """사용자가 체크카드를 고르면 전부 CHECK"""
    result = parse_file(*load("card_b.csv"), pay_method="CHECK")
    assert len(result) == 14
    assert all(row["payMethod"] == "CHECK" for row in result)
    assert result[0]["amount"] == 1450  # "1,450"
    assert result[0]["usedDate"] == "2026-04-01"  # "2026.04.01 08:30"
    assert result[8]["amount"] == -54300  # 취소 "-54,300"


def test_parse_hometax_is_cash():
    """홈택스 현금영수증 → 결제수단은 전부 CASH, 금액은 사용금액 칸"""
    result = parse_file(*load("hometax_cash.xls"))
    assert len(result) == 5
    assert all(row["payMethod"] == "CASH" for row in result)
    assert result[0]["rowNo"] == 3  # 1줄 안내 + 2줄 머리글 → 3번째 줄부터
    assert result[0]["merchantName"] == "(주)헤토"  # 정제 전 원본 그대로
    assert result[0]["amount"] == 15300


def test_parse_hometax_cancel_becomes_negative():
    """거래구분이 「취소거래」인데 금액이 양수면 음수로 바꿔요 → 합계 28,700원"""
    result = parse_file(*load("hometax_cash.xls"))
    assert result[3]["amount"] == -7000
    assert sum(row["amount"] for row in result) == 28700


def test_parse_validation_error_rows():
    """날짜 · 금액이 이상한 줄은 줄 번호를 모아서 VALIDATION"""
    text = (
        "승인일시,가맹점명,이용금액,사용구분\n"
        "2026-01-01,가게A,1000,신용\n"  # 2번째 줄: 정상
        "어제,가게B,1000,신용\n"  # 3번째 줄: 날짜 이상
        "2026-01-02,가게C,천원,신용\n"  # 4번째 줄: 금액 이상
    )
    with pytest.raises(CardParseError) as error:
        parse_file(text.encode("utf-8"), "a.csv")
    assert error.value.code == "VALIDATION"
    assert error.value.error_rows == [3, 4]


def test_parse_row_limit(monkeypatch):
    """
        행 수가 한도를 넘으면 ROW_LIMIT
        monkeypatch: 테스트 동안만 ROW_LIMIT 값을 1로 바꿔 줌 (테스트가 끝나면 원래대로)
    """
    monkeypatch.setattr(parser, "ROW_LIMIT", 1)
    with pytest.raises(CardParseError) as error:
        parse_file(*load("card_a.xlsx"))
    assert error.value.code == "ROW_LIMIT"