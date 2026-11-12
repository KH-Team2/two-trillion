from datetime import date

from app.services.benefit import BenefitInput, diagnose
from tests.criteria_data import criteria


def _result(results, code):
    return next(r for r in results if r.benefit_code == code)


def _input(**overrides):
    values = dict(
        tax_year=2026,
        total_salary=30_000_000,
        birth_date=date(1995, 5, 10),
        hire_date=date(2023, 3, 1),
        company_size="SME",
        homeless_head_yn="Y",
    )
    values.update(overrides)
    return BenefitInput(**values)


def test_sme_reduction_eligible_when_all_conditions_met():
    """조건을 다 맞으면 중소기업 감면 해당"""
    assert _result(diagnose(criteria(), _input()), "SME_YOUTH").eligible is True


def test_subtracts_military_service_period_from_age():
    """군 복무 기간만큼 나이를 빼준다"""
    # 입사 당시 만 35세 − 군 복무 24개월 → 33세
    data = _input(
        birth_date=date(1990, 1, 1), hire_date=date(2025, 3, 1), military_months=24
    )
    assert _result(diagnose(criteria(), data), "SME_YOUTH").eligible is True


def test_military_service_counts_up_to_72_months():
    """군 복무는 72개월까지만 인정"""
    # 입사 당시 만 42세 − 최대 72개월 → 36세 → 비해당
    data = _input(
        birth_date=date(1983, 1, 1), hire_date=date(2025, 3, 1), military_months=100
    )
    assert _result(diagnose(criteria(), data), "SME_YOUTH").eligible is False


def test_large_company_is_not_eligible_with_reason():
    """대기업이면 비해당과 이유"""
    result = _result(diagnose(criteria(), _input(company_size="LARGE")), "SME_YOUTH")
    assert result.eligible is False
    assert "중소기업 재직자만 받을 수 있어요" in result.reasons


def test_unknown_when_company_size_is_missing():
    """회사 규모가 없으면 판단 불가"""
    result = _result(diagnose(criteria(), _input(company_size=None)), "SME_YOUTH")
    assert result.eligible is None
    assert "회사 규모를 입력하면 진단할 수 있어요" in result.reasons


def test_not_eligible_after_5_years_from_join_date():
    """입사일부터 5년이 지나면 비해당"""
    assert (
        _result(
            diagnose(criteria(), _input(hire_date=date(2020, 1, 1))), "SME_YOUTH"
        ).eligible
        is False
    )


def test_not_eligible_with_reason_when_joined_after_tax_year():
    """입사일이 귀속 연도보다 뒤면 비해당과 이유"""
    # 2027년 입사인데 2026년 귀속을 진단 → 그 해에는 아직 다니지 않았으니 감면 대상이 아님
    result = _result(
        diagnose(criteria(), _input(hire_date=date(2027, 3, 1))), "SME_YOUTH"
    )
    assert result.eligible is False
    assert "2026년에는 아직 입사 전이에요" in result.reasons


def test_eligible_when_joined_on_last_day_of_tax_year():
    """귀속 연도 마지막 날 입사는 해당"""
    # 경계값: 12월 31일 입사는 그 해에 다닌 것으로 본다
    assert (
        _result(
            diagnose(criteria(), _input(hire_date=date(2026, 12, 31))), "SME_YOUTH"
        ).eligible
        is True
    )


def test_rent_not_eligible_when_not_homeless_head():
    """무주택 세대주가 아니면 월세 비해당"""
    result = _result(diagnose(criteria(), _input(homeless_head_yn="N")), "RENT")
    assert result.eligible is False


def test_housing_subscription_not_eligible_when_salary_over_70m():
    """총급여 7천만 초과면 주택청약 비해당"""
    assert (
        _result(
            diagnose(criteria(), _input(total_salary=75_000_000)), "HOUSING_SAVING"
        ).eligible
        is False
    )
