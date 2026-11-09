"""
    * 가맹점명 정제
    - 카드 내역의 가게 이름을 DB 목록 형태에 맞게 정제

    [ 정제 단계 ]
    1. 전각 -> 반각 (㈜ → (주), ＡＢＣ → ABC, ℃ → °C)
    2. 영문 대문자로 통일
    3. "법인" 표기 삭제 (주식회사, 주, 유한회사 삭제)
    4. "「한국지점」" 삭제
    5. 한글, 영문(대문자), 숫자만 남기고 띄어쓰기·괄호·기호 삭제
"""

# re: 정규표현식 도구
import re
# unicodedata: 글자를 표준 모양으로 바꿔주는 파이썬 도구
import unicodedata

# --------------정제 단계별 목록 데이터 ----------------------

# 3단계에서 삭제할 법인 표기 목록
# 괄호() 자체를 글자로 취급하여 찾기 위해 "\"를 붙임
# re.compile(): 정규표현식 문자열을 파싱하여 "정규표현식 객체"로 변환
_CORP = re.compile(
    r"(주식회사|유한회사|유한책임회사|사단법인|재단법인|사회적협동조합|협동조합"
    r"|\(주\)|\(유\)|\(사\)|\(재\)|\(합\)|㈔|㈕|주\))"
)

# 5단계에서 쓸 패턴
# : 숫자(0-9), 영문 대문자(A-Z), 한글(가-힣)이 아닌(^) 글자 찾아 지우기
_KEEP = re.compile(r"[^0-9A-Z가-힣]")

# --------------정제 단계별 목록 데이터 ---------------------- 

def normalized_name(name):
    """
        가맹점명 정제

        Args. 
            name: 정제 전 이름
        Return. 
            normalized: 정제 후 이름
    """
    # 0단계: None 값 검증
    if name is None:
        return None

    # 1단계: 전각 문자를 반각 문자로
    normalized = unicodedata.normalize("NFKC", str(name))

    # 2단계: 영문 대문자로
    normalized = normalized.upper()

    # 3단계: 법인 표기 삭제
    # 정규표현식_객체.sub("대체할_문자열", "적용할_문자열")
    normalized = _CORP.sub("", normalized)

    # 4단계: "「한국지점」" 삭제
    # re.sub(정규식, "대체할_문자열", "적용할_문자열")
    # strip(): 앞뒤 공백 제거, $: 문자열의 맨끝을 의미
    normalized = re.sub(r"한국지점$", "", normalized.strip())

    # 5단계: 한글, 영문, 숫자만 남기기
    normalized = _KEEP.sub("", normalized)

    return normalized