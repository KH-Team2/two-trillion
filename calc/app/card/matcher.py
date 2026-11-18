"""
    가맹점명에 따른 지출 분류
    
    [ 대조 규칙 ]
    - EXACT   : 정제한 가맹점명 == 목록 이름
    - CONTAINS: 목록 이름이 정제한 가맹점명 안에 **포함**됨
    
    - 일치하는(혹은 포함되는) 후보가 여러 개일 경우
        1. 분류 우선순위: 공제대상아님 -> 대중교통 -> 전통시장 -> 문화체육
        2. 같은 분류이면 CONTAINS보다 EXACT가 우선
        3. 길이가 긴 이름 먼저 
            예) 가맹점명 "통인시장떡볶이"
                : "시장"과 "통인시장" 둘 다 CONTAINS -> "통인시장" 기준 분류
        4. 우선순위, CONTAINS/EXACT, 이름글자수가 모두 같으면 LIST_ID가 작은 것으로
    
    - 일치 카테고리 없으면 일반(GENERAL), 목록 번호 없음(None)
"""
# @dataclass: 데이터 보관 용도의 클래스 생성기
from dataclasses import dataclass

from app.card.normalize import normalize_name

# 일치 카테고리 없을 시 분류코드 (일반)
GENERAL = "GENERAL"

# 분류 우선순위
CATEGORY_PRIORITY = {"EXCLUDED": 1, "TRANSIT": 2, "MARKET": 3, "CULTURE": 4}
OTHER_PRIORITY = 9

# frozen=True: 만든 뒤 값 수정 불가하게 설정 (목록 데이터 무결성 유지용)
@dataclass(frozen=True)
class MerchantRow:
    """
        MERCHANT_LIST 1행 (대조에 필요한 칸만).
    """ 
    list_id: int  # LIST_ID
    normalized_name: str    # NORMALIZED_NAME (정제된 이름)
    match_type: str         # MATCH_TYPE: "EXACT" 또는 "CONTAINS"
    category_code: str      # SPEND_CATEGORY.CATEGORY_CODE (예: "TRANSIT")

class MerchantMatcher:
    """
        목록을 메모리에 한 번 올리고 빠르게 분류
    
        목록 전체(8만행)를 가맹점명 하나 분류할 때마다 확인하는 것은 비효율적이므로,
        우선 exact와 contains로 분리한다.
        - exact    : {목록이름: 해당하는 행 리스트} 사전 
            → key(정제된가맹점이름)값으로 빠른 확인
            (목록이름 예: 서울교통공사, 광장시장 등)
        - contains : MATCH_TYPE=CONTAINS인 행 목록(약 700개) 
            → 가맹점명 안에 포함되는지 확인
    """
 
    def __init__(self, rows: list[MerchantRow]):
        self.exact: dict[str, list[MerchantRow]] = {}
        self.contains: list[MerchantRow] = []
        for row in rows:
            if row.match_type == "EXACT":
                # setdefault: 이 이름이 사전에 없으면 빈 목록을 먼저 만들고, 그 목록에 추가해요.
                self.exact.setdefault(row.normalized_name, []).append(row)
            else:
                self.contains.append(row)
 
    def classify(self, merchant_name: str | None) -> tuple[str, int | None]:
        """가맹점명 → (분류 코드, 일치한 LIST_ID). 일반이면 ("GENERAL", None)."""
        name = normalize_name(merchant_name)
        if not name:  # None이거나 빈 문자열이면 비교할 게 없어요
            return GENERAL, None
 
        # 1) 맞는 후보를 전부 모아요.
        #    EXACT: 사전에서 이름으로 바로 꺼내요 (없으면 빈 목록 []).
        #    CONTAINS: 목록 이름이 가맹점명 안에 있는지(in) 하나씩 확인해요.
        hits = list(self.exact.get(name, []))
        hits += [row for row in self.contains if row.normalized_name in name]
        if not hits:
            return GENERAL, None
 
        # 2) 정렬 기준(_rank)이 가장 작은 후보 1개를 골라요.
        best = min(hits, key=_rank)
        return best.category_code, best.list_id
 
 
def _rank(row: MerchantRow) -> tuple[int, int, int, int]:
    """후보끼리 비교할 순서. 튜플(묶음)은 앞 칸부터 비교해서, 같으면 다음 칸을 봐요.
 
    예: (2, 1, -5, 10) 과 (2, 2, -9, 3) → 첫 칸 같음 → 둘째 칸 1 < 2 → 앞쪽이 이겨요.
    """
    return (
        CATEGORY_PRIORITY.get(row.category_code, OTHER_PRIORITY),  # 1. 분류 우선순위
        1 if row.match_type == "EXACT" else 2,  # 2. EXACT 먼저
        # 3. 긴 이름 먼저 (- 를 붙여서 길수록 작은 수가 되게)
        -len(row.normalized_name),
        row.list_id,  # 4. LIST_ID 작은 것
    )
 