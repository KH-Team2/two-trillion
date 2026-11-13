"""
    목록 읽기(merchant_repo) 테스트
    (실행: calc 폴더에서 `pytest tests/test_card_merchant_repo.py`)

    - 진짜 DB 없이 확인하려고, DB 연결·커서 흉내를 내는 가짜(FakeConn · FakeCursor)를 만들어 넣어요.
    - 가짜 커서는 미리 정해 둔 결과 줄을 돌려주고, 실행된 SQL을 기억해 둬요.
"""

from app.card.matcher import GENERAL, MerchantRow
from app.card.merchant_repo import load_matcher, load_merchant_rows


class FakeCursor:
    """커서 흉내: execute()로 받은 SQL을 저장하고, for 문으로 돌리면 미리 정한 줄을 내줘요."""

    def __init__(self, rows):
        self.rows = rows
        self.sql = ""

    def execute(self, sql):
        self.sql = sql

    def __iter__(self):
        return iter(self.rows)

    # with 문에서 쓰려면 이 두 개가 있어야 해요 (들어갈 때 · 나올 때)
    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False


class FakeConn:
    """연결 흉내: cursor()를 부르면 가짜 커서를 돌려줘요."""

    def __init__(self, rows):
        self.cur = FakeCursor(rows)

    def cursor(self):
        return self.cur


def test_rows_become_merchant_rows():
    """DB 결과 한 줄이 MerchantRow 하나로 바뀌는지"""
    conn = FakeConn([(10, "서울교통공사", "EXACT", "TRANSIT")])

    rows = load_merchant_rows(conn)

    assert rows == [
        MerchantRow(
            list_id=10,
            normalized_name="서울교통공사",
            match_type="EXACT",
            category_code="TRANSIT",
        )
    ]


def test_list_id_float_becomes_int():
    """DB 숫자가 10.0(소수)으로 와도 10(정수)으로 바뀌는지"""
    conn = FakeConn([(10.0, "광장시장", "EXACT", "MARKET")])

    rows = load_merchant_rows(conn)

    assert rows[0].list_id == 10
    assert isinstance(rows[0].list_id, int)


def test_sql_reads_only_used_rows():
    """사용 중인 목록·분류만 읽는 조건이 SQL에 들어 있는지"""
    conn = FakeConn([])

    load_merchant_rows(conn)

    assert "m.USE_YN = 'Y'" in conn.cur.sql
    assert "c.USE_YN = 'Y'" in conn.cur.sql


def test_empty_table_gives_empty_list():
    """목록이 비어 있으면 빈 목록"""
    assert load_merchant_rows(FakeConn([])) == []


def test_load_matcher_classifies():
    """읽어 온 목록으로 만든 분류기가 실제로 분류하는지 (일치 · 포함 · 일반)"""
    conn = FakeConn(
        [
            (10, "서울교통공사", "EXACT", "TRANSIT"),
            (20, "시장", "CONTAINS", "MARKET"),
        ]
    )

    matcher = load_matcher(conn)

    assert matcher.classify("서울교통공사") == ("TRANSIT", 10)
    assert matcher.classify("통인시장떡볶이") == ("MARKET", 20)
    assert matcher.classify("스타벅스") == (GENERAL, None)