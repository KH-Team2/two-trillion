"""
    POST /card/parse 응답 모양 (F-07 · F-08)

    - CamelModel: 파이썬 이름(row_no)을 JSON에서는 camelCase(rowNo)로 바꿔 줘요 (팀 공통 규칙)
    - 백엔드 ParsedExpenseDTO · CardParseResultDTO와 칸 이름이 똑같아야 자동으로 담겨요
"""

from app.schemas.common import CamelModel


class CardRowResponse(CamelModel):
    """
        거래 1건
        JSON 예) {"rowNo": 5, "usedDate": "2026-03-02", "merchantName": "서울교통공사",
                  "payMethod": "CREDIT", "amount": 1550, "categoryCode": "TRANSIT", "listId": 1234}
    """

    row_no: int  # 파일에서 몇 번째 줄인지 (엑셀에서 보이는 줄 번호)
    used_date: str  # 사용일 "2026-03-02"
    merchant_name: str  # 가맹점명 (정제 전 원본 그대로)
    pay_method: str  # CREDIT · CHECK · CASH
    amount: int  # 금액 (취소 건은 음수)
    category_code: str  # 분류 코드 GENERAL · TRANSIT · MARKET · CULTURE · EXCLUDED
    list_id: int | None  # 일치한 목록 번호 (일반이면 None → JSON null)


class CardParseResponse(CamelModel):
    """성공 응답 전체: {"rowCount": 2, "rows": [...]}"""

    row_count: int  # 읽은 거래 건수
    rows: list[CardRowResponse]