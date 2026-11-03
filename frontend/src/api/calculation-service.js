// 세액 계산 API 연결 (F-03-01) · POST /api/calculations/{taxYear}
// back이 저장된 소득 · 카드 내역으로 계산 서버를 불러 계산하고 결과를 저장해요. 요청 본문은 없어요.
import { apiRequest, checkTaxYear } from './api-client.js';
import { parseCalculationResult } from './calculation-contract.js';
import { saveIncome } from './income-service.js';
import { getTaxYear } from '../utils/tax-year.js';

/** 계산 요청 → 응답 모양 검사 (빠진 값을 임의로 채우지 않아요) */
export async function requestCalculation(taxYear, options = {}) {
  checkTaxYear(taxYear);
  const body = await apiRequest(`/api/calculations/${taxYear}`, {
    ...options,
    method: 'POST',
    offlineMessage: '계산 서버에 연결할 수 없어요.',
  });
  return parseCalculationResult(body);
}

/** 입력값 저장(PUT) 후 계산(POST). 저장이 실패하면 계산하지 않아요. */
export async function saveAndCalculate(d, options = {}) {
  await saveIncome(d, options);
  return requestCalculation(getTaxYear(d), options);
}
