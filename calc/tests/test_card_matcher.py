"""
    가맹점명 분류(MerchantMatcher) 테스트
    (실행: 터미널에서 calc 폴더로 경로 잡고 `pytest tests/test_card_matcher.py`)

    - 진짜 DB 목록(8만 행) 대신, 테스트마다 필요한 목록 몇 줄만 직접 만들어서 넣어요.
    - classify(가맹점명)는 (분류 코드, LIST_ID)를 돌려줘요.
      예: ("TRANSIT", 10) → 대중교통, 목록 10번 행과 일치
"""
from app.card.matcher import GENERAL, MerchantMatcher, MerchantRow


def row(list_id, name, match_type, category_code):
    """
        목록 1행을 짧게 만드는 도우미 함수
        (MerchantRow(...)를 매번 길게 쓰지 않으려고 만듦)
    """
    return MerchantRow(
        list_id=list_id,
        normalized_name=name,
        match_type=match_type,
        category_code=category_code,
    )


# ── 기본: 일치 / 포함 / 없음 ──
def test_exact_match():
    """
        EXACT: 정제한 가맹점명이 목록 이름과 같으면 일치
    """
    matcher = MerchantMatcher([row(10, "서울교통공사", "EXACT", "TRANSIT")])
    assert matcher.classify("서울교통공사") == ("TRANSIT", 10)


def test_exact_after_normalize():
    """
        가맹점명을 먼저 정제한 뒤 비교해요
        "(주)티머니 " → "티머니" 로 정제 → 목록 "티머니"와 같음
    """
    matcher = MerchantMatcher([row(11, "티머니", "EXACT", "TRANSIT")])
    assert matcher.classify("(주)티머니 ") == ("TRANSIT", 11)


def test_exact_needs_same_name():
    """
        EXACT는 이름이 「똑같아야」 해요. 일부만 들어 있으면 일치 아님
        목록 "티머니" (EXACT) ↔ 가맹점 "티머니버스" → 일반
    """
    matcher = MerchantMatcher([row(11, "티머니", "EXACT", "TRANSIT")])
    assert matcher.classify("티머니버스") == (GENERAL, None)


def test_contains_match():
    """
        CONTAINS: 목록 이름이 가맹점명 「안에 들어 있으면」 일치
        목록 "통인시장" ↔ 가맹점 "통인시장 원조기름떡볶이"
    """
    matcher = MerchantMatcher([row(20, "통인시장", "CONTAINS", "MARKET")])
    assert matcher.classify("통인시장 원조기름떡볶이") == ("MARKET", 20)


def test_no_match_is_general():
    """
        어디에도 없으면 일반(GENERAL), 목록 번호는 None
    """
    matcher = MerchantMatcher([row(10, "서울교통공사", "EXACT", "TRANSIT")])
    assert matcher.classify("GS25 역삼점") == (GENERAL, None)


def test_empty_name_is_general():
    """
        가맹점명이 None이거나, 정제하고 나니 빈 글자("")면 일반
        "(주)" → 법인 표기만 있어서 정제 후 "" 가 됨
    """
    matcher = MerchantMatcher([row(10, "서울교통공사", "EXACT", "TRANSIT")])
    assert matcher.classify(None) == (GENERAL, None)
    assert matcher.classify("(주)") == (GENERAL, None)


# ── 우선순위 1: 분류 순서 (공제 대상 아님 → 대중교통 → 전통시장 → 문화체육) ──
def test_priority_excluded_first():
    """
        같은 이름이 「공제 대상 아님」과 「문화체육」 둘 다에 있으면
        → 공제 대상 아님(EXCLUDED)이 이겨요
    """
    matcher = MerchantMatcher([
        row(30, "상품권샵", "EXACT", "CULTURE"),
        row(31, "상품권샵", "EXACT", "EXCLUDED"),
    ])
    assert matcher.classify("상품권샵") == ("EXCLUDED", 31)


def test_priority_transit_before_market():
    """
        대중교통(2)이 전통시장(3)보다 먼저
    """
    matcher = MerchantMatcher([
        row(40, "시장역", "EXACT", "MARKET"),
        row(41, "시장역", "EXACT", "TRANSIT"),
    ])
    assert matcher.classify("시장역") == ("TRANSIT", 41)


def test_priority_market_before_culture():
    """
        전통시장(3)이 문화체육(4)보다 먼저
        (EXACT·CONTAINS가 섞여 있어도 분류 순서가 가장 먼저예요)
    """
    matcher = MerchantMatcher([
        row(50, "시장서점", "EXACT", "CULTURE"),
        row(51, "시장", "CONTAINS", "MARKET"),
    ])
    assert matcher.classify("시장서점") == ("MARKET", 51)


# ── 우선순위 2: 같은 분류면 EXACT 먼저 ──
def test_same_category_exact_first():
    """
        같은 대중교통 안에서 EXACT("서울역")와 CONTAINS("서울")가 둘 다 맞으면
        → EXACT가 이겨요 (CONTAINS 이름이 더 짧아도, 길이보다 EXACT가 먼저)
    """
    matcher = MerchantMatcher([
        row(60, "서울", "CONTAINS", "TRANSIT"),
        row(61, "서울역", "EXACT", "TRANSIT"),
    ])
    assert matcher.classify("서울역") == ("TRANSIT", 61)


# ── 우선순위 3: 그다음 긴 이름 먼저 ──
def test_longer_name_first():
    """
        "시장"과 "통인시장" 둘 다 CONTAINS로 맞으면 → 더 긴 "통인시장"
    """
    matcher = MerchantMatcher([
        row(70, "시장", "CONTAINS", "MARKET"),
        row(71, "통인시장", "CONTAINS", "MARKET"),
    ])
    assert matcher.classify("통인시장떡볶이") == ("MARKET", 71)


# ── 우선순위 4: 모두 같으면 LIST_ID 작은 것 ──
def test_smaller_list_id_last():
    """
        분류·EXACT/CONTAINS·이름 길이가 모두 같으면 → LIST_ID가 작은 행
        (목록에 넣은 순서와 상관없이 항상 같은 결과가 나오게)
    """
    matcher = MerchantMatcher([
        row(81, "망원", "CONTAINS", "MARKET"),
        row(80, "시장", "CONTAINS", "MARKET"),
    ])
    assert matcher.classify("망원시장") == ("MARKET", 80)