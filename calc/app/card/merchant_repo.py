"""
    분류용 가게 목록(MERCHANT_LIST)을 DB에서 읽어 오기

    [ 하는 일 ]
    - DB에서 「사용 중인」 목록 행만 SELECT 해서 MerchantRow 목록으로 바꿔 돌려줘요.
    - 그 목록으로 MerchantMatcher(분류기)를 만들어 줘요.

    [ 읽는 조건 ]
    - MERCHANT_LIST.USE_YN = 'Y'   : 사용 중인 목록 행만
    - SPEND_CATEGORY.USE_YN = 'Y'  : 사용 중인 분류에 속한 행만
      (04_분류쿼리.sql과 같은 조건)

    [ DB 연결(conn)은 밖에서 받아요 ]
    - 이 파일은 DB에 「어떻게 접속하는지」는 몰라요. 이미 접속된 연결(conn)을 받아서 쓰기만 해요.
    - 이유: 접속 코드는 PR #15의 app/db/oracle.py(get_pool)가 맡기로 해서, 여기서 또 만들면 중복이에요.
    - 덕분에 테스트할 때는 진짜 DB 대신 가짜 연결을 넣어서 확인할 수 있어요.
"""

from app.card.matcher import MerchantMatcher, MerchantRow

# 목록을 읽는 SQL
# - JOIN: 목록 행의 CATEGORY_ID로 SPEND_CATEGORY를 찾아 분류 코드(CATEGORY_CODE)를 같이 가져와요
#         (MerchantMatcher는 번호가 아니라 코드("TRANSIT" 등)로 우선순위를 정하기 때문)
# - ORDER BY LIST_ID: 매번 같은 순서로 읽게 하려고 (결과가 흔들리지 않게)
MERCHANT_SQL = """
    SELECT m.LIST_ID, m.NORMALIZED_NAME, m.MATCH_TYPE, c.CATEGORY_CODE
      FROM MERCHANT_LIST m
      JOIN SPEND_CATEGORY c ON c.CATEGORY_ID = m.CATEGORY_ID
     WHERE m.USE_YN = 'Y'
       AND c.USE_YN = 'Y'
     ORDER BY m.LIST_ID
"""


def load_merchant_rows(conn) -> list[MerchantRow]:
    """
        DB에서 사용 중인 목록 행을 전부 읽어 MerchantRow 목록으로 돌려줘요.

        - conn: 이미 접속된 DB 연결 객체
        - 돌려주는 값: [MerchantRow(...), MerchantRow(...), ...]  (약 8만 개)
    """
    # cursor(커서): SQL을 실행하고 결과를 한 줄씩 받아 오는 도구
    # with ... as cur: 다 쓰면 커서를 자동으로 닫아 줘요 (닫는 걸 깜빡해도 괜찮게)
    with conn.cursor() as cur:
        cur.execute(MERCHANT_SQL)
        # 결과 한 줄 = (LIST_ID, NORMALIZED_NAME, MATCH_TYPE, CATEGORY_CODE) 묶음(튜플)
        # → 한 줄씩 꺼내서 MerchantRow 하나로 바꿔요
        return [
            MerchantRow(
                # DB 숫자가 소수(float)로 올 수도 있어서 정수로 맞춤
                list_id=int(list_id),
                normalized_name=normalized_name,
                match_type=match_type,
                category_code=category_code,
            )
            for list_id, normalized_name, match_type, category_code in cur
        ]


def load_matcher(conn) -> MerchantMatcher:
    """
        DB 목록을 읽어서 바로 분류기(MerchantMatcher)로 만들어 줘요.

        사용 예)
            matcher = load_matcher(conn)          # 서버가 켜질 때 한 번만
            matcher.classify("㈜서울교통공사")      # → ("TRANSIT", 1234)
    """
    return MerchantMatcher(load_merchant_rows(conn))