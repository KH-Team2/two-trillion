"""
    가맹점명 정제 테스트
    (실행: 터미널에서 calc 폴더로 경로 잡고 `pytest tests/test_card_normalize.py`)
    assert 조건, "에러메시지(생략가능)"
    : 이 코드에서는 에러메시지를 작성하지 않았음.
    : 조건이 통과(True): 아무 일도 발생 X. 테스트 통과
    : 조건이 실패(False): AssertionError 발생
"""
from app.card.normalize import normalize_name

# ── 1단계: 전각 → 반각 ──
def test_step1_1():
    """
        ＡＢＣ(전각) → ABC(반각)
    """
    assert normalize_name("ＡＢＣ마트") == "ABC마트"

def test_step1_2():
    """
        ℃ → °C 
        (5단계 포함: ° 는 기호이므로 지우고 영문, 숫자, 한글만 남는다.)
    """
    assert normalize_name("36.5℃") == "365C"


# ── 2단계: 영문 대문자로 통일 ──
def test_step2():
    assert normalize_name("cafe 봄") == "CAFE봄"


# ── 3단계: 법인 표기 삭제 ──
def test_step3_1():
    """ 
        ㈜ → (주): 전각 -> 반각(1단계)
        → 표기 삭제
    """
    assert normalize_name("㈜스타벅스커피코리아") == "스타벅스커피코리아"

def test_step3_2():
    """
        법인 표기 삭제: (주) 삭제
    """
    assert normalize_name("(주)이마트") == "이마트"

def test_step3_3():
    """
        법인 표기 삭제: 주식회사 삭제
    """
    assert normalize_name("주식회사 카카오") == "카카오"

def test_step3_4():
    """
        법인 표기 삭제: 유한회사 삭제
    """
    assert normalize_name("유한회사 가나다") == "가나다"


def test_step3_5():
    """
        법인 표기 삭제: 주) (앞괄호 잘린 버전) 삭제
    """
    assert normalize_name("주)가나상사") == "가나상사"

def test_step3_6():
    """
        법인 표기 삭제: ㈔(사단법인) 삭제
    """
    assert normalize_name("㈔한국문화") == "한국문화"


# ── 4단계: 끝의 「한국지점」 삭제 ──
def test_step4_1():
    """
        가맹점명 끝의 한국지점 삭제
    """
    assert normalize_name("가나다 한국지점") == "가나다"

def test_step4_2():
    """
        가맹점명 끝에 공백이 있어도 삭제
    """
    assert normalize_name("가나다 한국지점  ") == "가나다"

def test_step4_3():
    """
        끝에 있지 않으면 삭제 안 함
    """
    assert normalize_name("한국지점앞식당") == "한국지점앞식당"


# ── 5단계: 한글 · 영문 · 숫자만 ──
def test_step5():
    """
        특수기호 및 공백 삭제
    """
    assert normalize_name("GS25 역삼-1호점!") == "GS25역삼1호점"


# ── 예외 입력 ──
def test_e_1():
    """
        빈 값 검증 (None)
    """
    assert normalize_name(None) is None

def test_e_2():
    """
        빈 값 검증 (빈 문자열)
    """
    assert normalize_name("") == ""

def test_e_3():
    """
        빈 값 검증 (앞의 단계를 거쳐 빈 문자열이 되는 경우)
    """
    assert normalize_name("(주) - ") == ""


