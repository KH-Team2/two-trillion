from app.services.card_deduction import calc_card_deduction
from app.services.models import CardSpend
from tests.criteria_data import criteria

GENERAL, TRANSIT, MARKET, CULTURE, EXCLUDED = 1, 2, 3, 4, 5


def _calc(salary, spends, children=0, year=2026):
    return calc_card_deduction(criteria(), year, salary, children, spends)


def _row(result, code):
    return next(d for d in result.details if d.type_code == code)


def test_deducts_only_amount_over_threshold():
    """문턱을 넘은 부분만 공제"""
    # (1,200만 − 문턱 750만) × 15% (노션 「한 장 요약」 예시)
    result = _calc(30_000_000, [CardSpend(GENERAL, "CREDIT", 12_000_000)])
    assert result.total_deduction == 675_000


def test_threshold_is_consumed_by_credit_card_first():
    """문턱은 신용카드부터 뺀다"""
    # 문턱 750만: 신용 500만 전부 + 체크 250만 → 체크 250만 × 30%
    result = _calc(
        30_000_000,
        [
            CardSpend(GENERAL, "CREDIT", 5_000_000),
            CardSpend(GENERAL, "CHECK", 5_000_000),
        ],
    )
    assert _row(result, "CARD_CREDIT").deduction_amount == 0
    assert _row(result, "CARD_CHECK").deduction_amount == 750_000


def test_returns_six_card_rows():
    """카드 행은 6개"""
    result = _calc(30_000_000, [])
    assert len([d for d in result.details if d.kind == "CARD"]) == 6


def test_limit_row_is_negative_when_over_base_limit():
    """기본한도를 넘으면 한도 행이 음수"""
    # (4,000만 − 750만) × 15% = 487.5만 → 기본한도 300만 → 187.5만 초과
    result = _calc(30_000_000, [CardSpend(GENERAL, "CREDIT", 40_000_000)])
    assert result.total_deduction == 3_000_000
    assert _row(result, "CARD_LIMIT_BASE").deduction_amount == -1_875_000


def test_card_rows_sum_to_actual_deduction():
    """카드 행을 다 더하면 실제 공제액"""
    result = _calc(
        30_000_000,
        [
            CardSpend(GENERAL, "CREDIT", 40_000_000),
            CardSpend(TRANSIT, "CREDIT", 10_000_000),
        ],
    )
    assert sum(d.deduction_amount for d in result.details) == result.total_deduction


def test_public_transport_gets_extra_limit():
    """대중교통은 추가한도까지 더 받는다"""
    # 일반 487.5만 + 대중교통 400만 = 887.5만 → 300만 + min(587.5만, 400만, 추가한도 300만) = 600만
    result = _calc(
        30_000_000,
        [
            CardSpend(GENERAL, "CREDIT", 40_000_000),
            CardSpend(TRANSIT, "CREDIT", 10_000_000),
        ],
    )
    assert result.total_deduction == 6_000_000


def test_base_limit_is_3_5m_with_one_child_in_2026():
    """2026년은 자녀 1명이면 기본한도 350만"""
    result = _calc(30_000_000, [CardSpend(GENERAL, "CREDIT", 40_000_000)], children=1)
    assert result.total_deduction == 3_500_000


def test_base_limit_is_3m_even_with_child_in_2025():
    """2025년은 자녀가 있어도 기본한도 300만"""
    result = _calc(
        30_000_000, [CardSpend(GENERAL, "CREDIT", 40_000_000)], children=1, year=2025
    )
    assert result.total_deduction == 3_000_000


def test_culture_spend_counts_as_general_when_salary_over_70m():
    """총급여 7천만 초과면 문화체육은 일반으로"""
    # 문턱 2,000만(8,000만 × 25%) · 문화체육 2,500만(체크) → 체크카드(일반) 행으로 → 500만 × 30%
    result = _calc(80_000_000, [CardSpend(CULTURE, "CHECK", 25_000_000)])
    assert _row(result, "CARD_CHECK").deduction_amount == 1_500_000


def test_excludes_non_deductible_spend():
    """공제 대상 아님은 뺀다"""
    result = _calc(30_000_000, [CardSpend(EXCLUDED, "CREDIT", 50_000_000)])
    assert result.total_deduction == 0
