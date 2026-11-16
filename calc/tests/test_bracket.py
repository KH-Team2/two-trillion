from app.services.bracket import calc_bracket
from tests.criteria_data import BRACKETS


def test_earned_income_deduction_for_30m_salary():
    """근로소득공제 3천만"""
    # 750만 + (3,000만 − 1,500만) × 15% (노션 「한 장 요약」 예시)
    assert calc_bracket(30_000_000, BRACKETS[(2026, "EARNED_DEDUCTION")]) == 9_750_000


def test_calculated_tax_for_taxable_income_15_375_000():
    """산출세액 과세표준 1537만5천"""
    # 84만 + (1,537.5만 − 1,400만) × 15%
    assert calc_bracket(15_375_000, BRACKETS[(2026, "TAX_RATE")]) == 1_046_250


def test_earned_credit_is_55_percent_up_to_1_3m_floored_to_won():
    """근로소득세액공제 130만 이하는 55퍼센트 원미만 버림"""
    assert calc_bracket(1_046_250, BRACKETS[(2026, "EARNED_CREDIT")]) == 575_437


def test_bracket_boundary_belongs_to_lower_bracket():
    """구간 경계는 이하쪽"""
    # 1,400만은 첫 구간(이하) → 6%
    assert calc_bracket(14_000_000, BRACKETS[(2026, "TAX_RATE")]) == 840_000


def test_zero_amount_returns_zero():
    """0원이면 0"""
    assert calc_bracket(0, BRACKETS[(2026, "TAX_RATE")]) == 0
