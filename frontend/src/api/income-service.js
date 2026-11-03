// 소득 저장 · 조회 API 연결 (S-02 → INCOME) · GET · PUT /api/incomes/{taxYear}
// PUT은 보낸 값으로 한 해 소득 전체를 덮어써요 → 항상 모든 칸을 보내요.
import { apiError, apiRequest, checkTaxYear } from './api-client.js';
import { getTaxYear } from '../utils/tax-year.js';

// 선택 입력(보험료 · 저축 등)은 비어 있으면 0원 (back 기본값과 같음)
const won = (value) => (Number.isFinite(value) ? Math.max(0, Math.round(value)) : 0);
const count = (value) => (Number.isInteger(value) ? Math.max(0, value) : 0);
// 필수 입력(총급여 · 기납부세액)은 비어 있거나 음수면 0으로 바꾸지 않고 막아요
const requiredWon = (value, message) => {
  if (!Number.isFinite(value) || value < 0) throw apiError('VALIDATION', message);
  return Math.round(value);
};
const SME_YN = { 예: 'Y', 해당: 'Y', 아니요: 'N', 미해당: 'N' };

/**
 * 화면 입력값 → back IncomeRequestDTO 이름.
 * - 월세는 월 납부액(rent)을 그대로 보내요.
 * - 연금저축과 IRP는 따로 보내요 (back에 칸이 따로 있음).
 * - 중소기업 감면 신청 여부는 Y/N만 저장돼요. '확인 필요'를 N으로 바꾸지 않고 선택하라고 안내해요.
 */
export function incomeRequest(d) {
  const smeReductionYn = SME_YN[d.sme];
  if (!smeReductionYn) {
    throw apiError('VALIDATION', '중소기업 취업자 감면 신청 여부(예/아니요)를 선택해주세요.');
  }
  return {
    totalSalary: requiredWon(d.salary, '총급여를 확인해주세요.'),
    withheldTax: requiredWon(d.paid, '기납부세액을 확인해주세요.'),
    nationalPension: won(d.nationalPension),
    healthInsurance: won(d.healthEmploymentInsurance),
    dependents: count(d.family),
    children: count(d.children),
    monthlyRent: won(d.rent),
    housingSaving: won(d.housingSaving),
    pensionSaving: won(d.pensionSaving),
    irpSaving: won(d.irp),
    smeReductionYn,
  };
}

/** 소득 저장 (PUT) */
export function saveIncome(d, options = {}) {
  const taxYear = getTaxYear(d);
  checkTaxYear(taxYear);
  return apiRequest(`/api/incomes/${taxYear}`, {
    ...options,
    method: 'PUT',
    body: incomeRequest(d),
    offlineMessage: '소득 저장 서버에 연결할 수 없어요.',
  });
}

/** 소득 조회 (GET) · 저장된 소득이 없으면 code 'NOT_FOUND' 오류 */
export function fetchIncome(taxYear, options = {}) {
  checkTaxYear(taxYear);
  return apiRequest(`/api/incomes/${taxYear}`, {
    ...options,
    offlineMessage: '소득 조회 서버에 연결할 수 없어요.',
  });
}
