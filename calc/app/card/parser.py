"""
    카드사 · 홈택스 파일 해석 (F-07 · F-02-02)

    카드사마다 칸 이름과 모양이 달라서, 파일을 읽은 뒤
    공통 포맷 4칸(승인일시 · 가맹점명 · 이용금액 · 사용구분)만 뽑아
    같은 모양의 행 목록으로 바꿔요.

    [ 흐름 ]
    1. read_rows()   : 파일(csv · xlsx · xls)을 글자 표로 읽기
    2. find_header() : 위에서 20줄 안에서 머리글(칸 제목) 줄 찾기
    3. to_date() · to_amount() : 날짜 · 금액 글자를 값으로 바꾸기
    4. 결제수단 정하기 : 신용 = CREDIT · 체크 = CHECK · 현금영수증 = CASH
    5. parse_file()  : 1~4를 묶어서 행 목록 만들기

    [ 오류 코드 ] (back이 화면 문구로 바꿔서 보여 줌)
    - UNSUPPORTED_FORMAT  : 지원하지 않는 확장자 · 4칸을 못 찾는 양식
    - PAY_METHOD_REQUIRED : 사용구분 칸이 없어 신용/체크 선택이 필요함
    - ROW_LIMIT           : 행 수 한도 초과
    - VALIDATION          : 날짜 · 금액 · 결제수단이 이상한 행이 있음 (행 번호 함께)
"""
# csv: 쉼표로 나뉜 글자 파일을 읽는 파이썬 기본 도구
import csv

# io: 메모리에 있는 글자/바이트를 "파일처럼" 다루게 해 주는 도구
import io

# re: 정규표현식(글자 모양 찾기) 도구
import re
from datetime import date

# Decimal: 소수점 계산을 정확하게 하는 숫자 (float는 1550.0000001 같은 오차가 생길 수 있음)
from decimal import Decimal, InvalidOperation

# pandas: 엑셀 표를 읽는 도구 (xlsx · xls 둘 다 calamine 엔진으로 읽음)
import pandas as pd

# 업로드 1건에 받을 수 있는 최대 행 수 (팀 결정 전 임시값)
ROW_LIMIT = 10_000

# 머리글을 찾을 때 위에서 몇 줄까지 볼지 (카드사 파일은 위에 안내 문구 줄이 있음)
HEADER_SEARCH_LINES = 20

# 공통 4칸 → 카드사 · 홈택스가 실제로 쓰는 칸 이름 후보
# 새 카드사 양식이 들어오면 여기에 이름만 추가하면 돼요
HEADER_CANDIDATES = {
    "usedDate": ["승인일시", "이용일시", "거래일시", "이용일자"],
    "merchantName": ["가맹점명", "가맹점", "이용가맹점"],
    "amount": ["이용금액", "승인금액(원)", "승인금액", "사용금액", "금액"],
    "payMethod": ["이용카드구분", "사용구분", "카드구분"],  # 없으면 사용자가 선택
}

# 취소 건인지 알려 주는 칸 (값에 "취소"가 들어 있으면 취소 건)
CANCEL_COLUMNS = ["승인상태", "거래구분"]

# 홈택스 현금영수증 양식에만 있는 칸 → 이 칸이 있으면 결제수단은 무조건 CASH
HOMETAX_COLUMN = "발행구분"

# 사용자가 고를 수 있는 결제수단 (사용구분 칸이 없는 카드사 파일일 때)
USER_PAY_METHODS = ("CREDIT", "CHECK")

# 날짜 모양: 2026-03-02 · 2026.04.01 · 2026/04/01 · 20260305 (뒤에 시간이 붙어도 됨)
_DATE = re.compile(r"^(\d{4})[-./]?(\d{1,2})[-./]?(\d{1,2})")


class CardParseError(Exception):
    """
        파일 해석 실패를 알리는 오류
        - code: 오류 코드 (UNSUPPORTED_FORMAT 등)
        - message: 화면에 보여 줄 문구
        - error_rows: 문제 있는 행 번호 목록 (VALIDATION일 때만)
    """

    def __init__(self, code, message, error_rows=None):
        super().__init__(message)
        self.code = code
        self.message = message
        self.error_rows = error_rows or []


# ───────────────────────── 1. 파일 읽기 ─────────────────────────
def read_rows(content: bytes, filename: str) -> list[list[str]]:
    """
        파일 내용(바이트) → 글자 표 [[칸, 칸, ...], ...]
        모든 칸은 글자(str)로 읽고, 빈 칸은 ""로 바꿔요.
    """
    # 파일 이름 끝(확장자)을 소문자로: "내역.XLSX" → "xlsx"
    ext = filename.rsplit(".", 1)[-1].lower() if "." in filename else ""

    if ext == "csv":
        # 한국 카드사 csv는 cp949(옛 윈도우 한글)가 많아서, utf-8이 안 되면 cp949로 다시 읽어요
        # utf-8-sig: 파일 맨 앞의 보이지 않는 표시(BOM)까지 알아서 빼 주는 utf-8
        try:
            text = content.decode("utf-8-sig")
        except UnicodeDecodeError:
            text = content.decode("cp949")
        return [[cell.strip() for cell in line] for line in csv.reader(io.StringIO(text))]

    if ext in ("xlsx", "xls"):
        # header=None: 첫 줄을 머리글로 쓰지 말고 전부 내용으로 읽기 (머리글은 find_header가 찾음)
        # dtype=str: 모든 칸을 글자로 읽기
        # engine="calamine": xlsx와 옛날 xls를 둘 다 읽는 엔진
        table = pd.read_excel(io.BytesIO(content), header=None, dtype=str, engine="calamine")
        table = table.fillna("")  # 빈 칸(NaN)을 ""로
        return [[str(cell).strip() for cell in line] for line in table.values.tolist()]

    raise CardParseError("UNSUPPORTED_FORMAT", "csv · xlsx · xls 파일만 올릴 수 있어요.")


# ───────────────────────── 2. 머리글 찾기 ─────────────────────────
def find_header(rows: list[list[str]]) -> tuple[int, dict[str, int]]:
    """
        위에서 HEADER_SEARCH_LINES줄 안에서 머리글 줄을 찾아요.
        돌려주는 값: (머리글 줄 번호(0부터), {칸 이름: 칸 위치})
        예) (3, {"usedDate": 0, "merchantName": 3, "amount": 4, "payMethod": 2})
    """
    for line_index, line in enumerate(rows[:HEADER_SEARCH_LINES]):
        # 칸 제목의 띄어쓰기를 빼고 비교 ("가맹점 명" 같은 경우 대비)
        titles = [cell.replace(" ", "") for cell in line]
        columns = {}
        for key, candidates in HEADER_CANDIDATES.items():
            for position, title in enumerate(titles):
                if title in candidates:
                    columns[key] = position
                    break
        # 꼭 필요한 3칸(날짜 · 가맹점 · 금액)이 다 있으면 이 줄이 머리글
        if all(key in columns for key in ("usedDate", "merchantName", "amount")):
            # 취소 칸 · 홈택스 칸 위치도 같이 기억해 둠 (없으면 안 넣음)
            for position, title in enumerate(titles):
                if title in CANCEL_COLUMNS:
                    columns["cancel"] = position
                if title == HOMETAX_COLUMN:
                    columns["hometax"] = position
            return line_index, columns

    raise CardParseError("UNSUPPORTED_FORMAT", "아직 지원하지 않는 양식이에요.")


# ───────────────────────── 3. 값 바꾸기 ─────────────────────────
def to_date(value: str) -> date:
    """
        날짜 글자 → 날짜 (시간은 버려요 · 팀 결정: USED_DATE에는 날짜만)
        "2026-03-02 08:12:33" · "2026.04.01 08:30" · "20260305" → date(2026, 3, 2) 등
        모양이 이상하거나 없는 날짜(2월 30일 등)면 ValueError
    """
    found = _DATE.match(value.strip())
    if not found:
        raise ValueError(f"날짜 모양이 아님: {value}")
    year, month, day = (int(part) for part in found.groups())
    return date(year, month, day)  # 없는 날짜면 여기서 ValueError가 나요


def to_amount(value: str) -> int:
    """
        금액 글자 → 원 단위 정수
        "1,450" · "-54,300" · "12000" · "12000.0" · "3,200원" → 1450 · -54300 · 12000 · 12000 · 3200
        숫자가 아니거나 소수점 아래 값이 있으면(12.5) ValueError
    """
    cleaned = value.replace(",", "").replace("원", "").replace(" ", "")
    try:
        number = Decimal(cleaned)
    except InvalidOperation:
        raise ValueError(f"금액 모양이 아님: {value}") from None
    if number != number.to_integral_value():  # 12.5처럼 원 단위가 아니면
        raise ValueError(f"원 단위가 아님: {value}")
    return int(number)


def to_pay_method(value: str) -> str:
    """
        사용구분 칸 글자 → 결제수단 코드
        "신용" · "신용카드" → CREDIT / "체크" · "체크카드" → CHECK
    """
    if "신용" in value:
        return "CREDIT"
    if "체크" in value:
        return "CHECK"
    raise ValueError(f"결제수단을 알 수 없음: {value}")


# ───────────────────────── 5. 전체 해석 ─────────────────────────
def parse_file(content: bytes, filename: str, pay_method: str | None = None) -> list[dict]:
    """
        파일 → 공통 포맷 행 목록

        Args.
            content: 파일 내용(바이트)
            filename: 원본 파일 이름 (확장자 확인용)
            pay_method: 사용자가 고른 결제수단 (사용구분 칸이 없을 때만 씀 · CREDIT/CHECK)
        Return.
            [{"rowNo": 5, "usedDate": "2026-03-02", "merchantName": "서울교통공사",
              "payMethod": "CREDIT", "amount": 1550}, ...]
            rowNo = 엑셀에서 보이는 줄 번호 (1부터)
    """
    rows = read_rows(content, filename)
    header_index, columns = find_header(rows)

    # 머리글 아래 줄들이 거래 내역 · 완전히 빈 줄은 건너뜀
    # (줄 번호, 칸 목록) 짝으로 묶어 둠: 줄 번호 = 0부터 센 위치 + 1
    data = [
        (index + 1, line)
        for index, line in enumerate(rows[header_index + 1:], start=header_index + 1)
        if any(cell != "" for cell in line)
    ]
    if len(data) > ROW_LIMIT:
        raise CardParseError("ROW_LIMIT", f"{ROW_LIMIT:,}행 이하 파일만 올릴 수 있어요.")

    # 4. 결제수단 정하기 (파일 전체에 한 번)
    #    - 홈택스 양식 → 전부 CASH
    #    - 사용구분 칸 있음 → 행마다 그 칸을 읽음 (fixed_pay = None)
    #    - 사용구분 칸 없음 → 사용자가 고른 값 · 안 골랐으면 PAY_METHOD_REQUIRED
    if "hometax" in columns:
        fixed_pay = "CASH"
    elif "payMethod" in columns:
        fixed_pay = None
    elif pay_method in USER_PAY_METHODS:
        fixed_pay = pay_method
    else:
        raise CardParseError("PAY_METHOD_REQUIRED", "신용카드인지 체크카드인지 선택해 주세요.")

    def cell(line, key):
        """한 줄에서 key 칸 값 꺼내기 (칸이 모자란 줄이면 "")"""
        position = columns[key]
        return line[position] if position < len(line) else ""

    results = []
    error_rows = []
    for row_no, line in data:
        try:
            merchant = cell(line, "merchantName")
            if not merchant:
                raise ValueError("가맹점명 없음")
            amount = to_amount(cell(line, "amount"))
            # 취소 칸에 "취소"가 있는데 금액이 양수로 적혀 있으면 음수로 바꿈
            # (계산 서버는 취소 건을 음수로 받아 합계에서 빼요)
            if "cancel" in columns and "취소" in cell(line, "cancel") and amount > 0:
                amount = -amount
            results.append({
                "rowNo": row_no,
                "usedDate": to_date(cell(line, "usedDate")).isoformat(),  # "2026-03-02"
                "merchantName": merchant,  # 정제 전 원본 그대로 (EXPENSE에 원본 저장)
                "payMethod": fixed_pay or to_pay_method(cell(line, "payMethod")),
                "amount": amount,
            })
        except ValueError:
            error_rows.append(row_no)

    if error_rows:
        raise CardParseError(
            "VALIDATION",
            f"{', '.join(map(str, error_rows))}번째 줄의 날짜 · 금액 · 결제수단을 확인해 주세요.",
            error_rows,
        )
    return results