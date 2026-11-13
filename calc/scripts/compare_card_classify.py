"""
    파이썬 분류 결과 vs SQL(04_분류쿼리.sql) 분류 결과 비교

    [ 왜 하나 ]
    - 같은 분류 규칙이 SQL(04번)과 파이썬(matcher.py) 두 군데에 있어요.
    - EXPENSE 전체(가상 10,453건)를 두 방법으로 분류해서, 건마다 결과가 같은지 확인해요.
    - 다른 건이 0건이면 파이썬 분류(F-08)가 SQL과 똑같이 동작한다는 뜻이에요.

    [ 먼저 할 일 ]
    - SQL Developer에서 04번 분류 쿼리를 EXPENSE 전체에 돌려 둬야 해요
      (EXPENSE.CATEGORY_ID · LIST_ID에 SQL 정답이 들어 있어야 함)
    - calc/.env 에 DB_USERNAME · DB_PASSWORD · DB_DSN 입력
      (예: DB_DSN=localhost:1521/XE · 서비스 이름은 SQL Developer 접속 정보에서 확인)

    [ 실행 ]
    - calc 폴더에서:  python -m scripts.compare_card_classify
      (-m 으로 실행해야 app.card... 를 찾을 수 있어요)

    [ DB를 바꾸나요? ]
    - 아니요. SELECT만 해요 (읽기만).
"""

import os
import sys

import oracledb

from app.card.merchant_repo import load_matcher
from app.card.normalize import normalize_name

# 다른 건을 화면에 몇 개까지 보여줄지 (너무 많으면 화면이 넘쳐서)
SHOW_LIMIT = 30


def load_env():
    """
        calc/.env 파일을 읽어 환경변수로 넣어요.
        - python-dotenv(.env 읽는 라이브러리)가 설치돼 있으면 그걸 쓰고,
          없으면 이미 설정된 환경변수만 써요.
    """
    try:
        from dotenv import load_dotenv

        load_dotenv()
    except ImportError:
        pass


def connect():
    """
        .env 값으로 DB에 접속해요.
        - os.environ.get("이름"): 환경변수 값 꺼내기 (없으면 None)
    """
    user = os.environ.get("DB_USERNAME")
    password = os.environ.get("DB_PASSWORD")
    dsn = os.environ.get("DB_DSN")
    if not (user and password and dsn):
        # 값이 하나라도 비어 있으면 안내하고 끝내요
        sys.exit("calc/.env 에 DB_USERNAME · DB_PASSWORD · DB_DSN 을 넣어 주세요.")
    return oracledb.connect(user=user, password=password, dsn=dsn)


def load_category_codes(conn) -> dict[int, str]:
    """
        분류 번호 → 분류 코드 사전 만들기
        예) {1: "GENERAL", 2: "TRANSIT", ...}
        - EXPENSE에는 번호(CATEGORY_ID)가 들어 있고, 파이썬은 코드("TRANSIT")를 돌려줘서
          둘을 같은 모양으로 맞추려고 필요해요.
    """
    with conn.cursor() as cur:
        cur.execute("SELECT CATEGORY_ID, CATEGORY_CODE FROM SPEND_CATEGORY")
        return {int(cid): code for cid, code in cur}


def load_expenses(conn) -> list[tuple]:
    """
        EXPENSE 전체 읽기 → [(지출번호, 가맹점명, SQL 분류번호, SQL 목록번호), ...]
        - SQL 목록번호(LIST_ID)는 일반이면 None(NULL)
    """
    with conn.cursor() as cur:
        cur.execute(
            "SELECT EXPENSE_ID, MERCHANT_NAME, CATEGORY_ID, LIST_ID"
            " FROM EXPENSE ORDER BY EXPENSE_ID"
        )
        return cur.fetchall()  # fetchall: 결과 전체를 한 번에 목록으로 받기


def to_int(value):
    """DB 숫자를 정수로 (NULL이면 None 그대로)"""
    return None if value is None else int(value)


def main():
    load_env()
    conn = connect()
    try:
        # 1) 파이썬 분류기 준비 (목록 약 8만 행을 메모리에 올림)
        matcher = load_matcher(conn)
        print(f"목록: EXACT {len(matcher.exact):,}개 이름 · CONTAINS {len(matcher.contains):,}개")

        # 2) SQL 정답과 분류 코드 사전 읽기
        codes = load_category_codes(conn)
        expenses = load_expenses(conn)
        print(f"비교할 지출: {len(expenses):,}건")
    finally:
        # 다 읽었으면 DB 연결은 닫아요 (아래 비교는 메모리에서만 해요)
        conn.close()

    # 3) 건마다 비교
    diffs = []  # 다른 건을 모아 둘 목록
    for expense_id, merchant_name, sql_cat_id, sql_list_id in expenses:
        sql_code = codes.get(int(sql_cat_id), f"?{sql_cat_id}")  # 번호 → 코드
        sql_list_id = to_int(sql_list_id)

        py_code, py_list_id = matcher.classify(merchant_name)

        if (py_code, py_list_id) != (sql_code, sql_list_id):
            diffs.append(
                (expense_id, merchant_name, sql_code, sql_list_id, py_code, py_list_id)
            )

    # 4) 결과 출력
    if not diffs:
        print(f"✅ 전부 일치 ({len(expenses):,}건) · 파이썬 분류 = SQL 분류")
        return

    print(f"❌ 다른 건 {len(diffs):,}건 (앞 {SHOW_LIMIT}건만 표시)")
    print("지출번호 | 가맹점명 | 정제한 이름 | SQL(분류, 목록) | 파이썬(분류, 목록)")
    for expense_id, name, sql_code, sql_lid, py_code, py_lid in diffs[:SHOW_LIMIT]:
        print(
            f"{expense_id} | {name} | {normalize_name(name)} | "
            f"({sql_code}, {sql_lid}) | ({py_code}, {py_lid})"
        )

    # 어느 쪽으로 많이 어긋났는지 한눈에 보기 (예: SQL은 TRANSIT인데 파이썬은 GENERAL)
    summary: dict[tuple, int] = {}
    for _, _, sql_code, _, py_code, _ in diffs:
        key = (sql_code, py_code)
        summary[key] = summary.get(key, 0) + 1
    print("\n[ 어긋난 방향별 건수 ] SQL → 파이썬")
    for (sql_code, py_code), count in sorted(summary.items(), key=lambda x: -x[1]):
        same = " (분류는 같고 목록 번호만 다름)" if sql_code == py_code else ""
        print(f"{sql_code} → {py_code}: {count}건{same}")


if __name__ == "__main__":
    main()