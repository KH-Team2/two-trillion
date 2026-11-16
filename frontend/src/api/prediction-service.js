// 조건 변경 시뮬레이션 · 결과 이력 API 연결 (F-04-01 · F-04-02)
//   POST /api/simulations/{taxYear}  바꾼 조건으로 다시 계산 → 시나리오로 저장 (INCOME · EXPENSE는 그대로)
//   GET  /api/predictions?year=      현재(기준 결과) + 시나리오 목록
//   GET  /api/predictions/{id}       결과 하나 다시 열기 (항목별 내역 포함)
import { apiError, apiRequest, checkTaxYear } from './api-client.js';
import { parseCalculationResult } from './calculation-contract.js';
import { incomeRequest } from './income-service.js';
import { getTaxYear } from '../utils/tax-year.js';

// 화면 분류 → SPEND_CATEGORY.CATEGORY_ID (1 일반 · 2 대중교통 · 3 전통시장 · 4 문화체육 · 5 공제 대상 아님)
// 업로드 거래는 한글 이름, 가상 추가 사용액은 영문 코드로 들어와요
const CATEGORY_ID = {
  일반: 1,
  GENERAL: 1,
  대중교통: 2,
  TRANSIT: 2,
  전통시장: 3,
  MARKET: 3,
  문화체육: 4,
  CULTURE: 4,
  '공제 대상 아님': 5,
  '공제 제외': 5,
  EXCLUDED: 5,
};
// 화면 결제수단 → EXPENSE.PAY_METHOD
const PAY_METHOD = {
  신용카드: 'CREDIT',
  CREDIT: 'CREDIT',
  체크카드: 'CHECK',
  CHECK: 'CHECK',
  현금영수증: 'CASH',
  CASH: 'CASH',
};
export const SCENARIO_NAME_MAX = 16; // PREDICTION.SCENARIO_NAME (back 검사와 같음)
export const DEFAULT_SCENARIO_NAME = '조건 변경 시나리오';

/**
 * 무주택 세대주 여부 → Y/N.
 * - 주택 「무주택」 + 세대주 「예」일 때만 Y
 * - 세대주 「아니요」 또는 「주택 보유」면 N
 * - 그 밖(「확인 필요」)은 N으로 바꾸지 않고 선택하라고 안내해요 (감면 신청 여부와 같은 방식)
 */
export function homelessHeadYn(d) {
  if (d.headOfHousehold === '아니요' || d.housing === '주택 보유') return 'N';
  if (d.headOfHousehold === '예' && d.housing === '무주택') return 'Y';
  throw apiError('VALIDATION', '주택 보유 상태와 무주택 세대주 여부를 선택해주세요.');
}

/**
 * 카드 사용액 → 분류 + 결제수단별 합계 (back CardSpendDTO 목록).
 * - 업로드 거래: 그 연도 · 집계 포함(중복 확인 중 · 제외 아님) · 결제수단을 아는 거래만
 * - 가상 추가 사용액(simulationSpend): 0원보다 큰 줄만
 * - 기존 계산(back)이 EXPENSE를 분류 · 결제수단별로 더하는 것과 같은 모양이에요
 */
export function cardSpends(transactions = [], simulationSpend = [], taxYear) {
  const sums = new Map();
  const add = (category, payment, amount) => {
    const categoryId = CATEGORY_ID[category] ?? 1; // 분류를 모르면 일반 (업로드 화면 기본값과 같음)
    const payMethod = PAY_METHOD[payment];
    if (!payMethod || !Number.isFinite(amount)) return;
    const key = `${categoryId}|${payMethod}`;
    sums.set(key, (sums.get(key) || 0) + Math.round(amount));
  };
  for (const t of transactions) {
    if (['pending', 'excluded'].includes(t.duplicateState || '')) continue;
    if (Number(String(t.date || '').slice(0, 4)) !== taxYear) continue;
    add(t.category, t.paymentMethod, Number(t.amount));
  }
  for (const row of simulationSpend || []) {
    const amount = Number(row.amount);
    if (!Number.isFinite(amount) || amount < 0) {
      throw apiError('VALIDATION', '가상 추가 사용액은 0원 이상으로 입력해주세요.');
    }
    if (amount > 0) add(row.category, row.payment, amount);
  }
  return [...sums.entries()]
    .map(([key, amount]) => {
      const [categoryId, payMethod] = key.split('|');
      // 취소 건(음수)이 더 많아 합계가 음수면 0 (back 검사: 0원 이상)
      return { categoryId: Number(categoryId), payMethod, amount: Math.max(0, amount) };
    })
    .sort((a, b) => a.categoryId - b.categoryId || a.payMethod.localeCompare(b.payMethod));
}

/** 화면 입력 → back SimulationRequestDTO (바꾼 값만이 아니라 입력값 전부) */
export function simulationRequest(d, transactions, basePredictionId) {
  if (!Number.isInteger(basePredictionId)) {
    throw apiError('NO_BASE', '먼저 기존 정보로 계산해 주세요. 비교할 기준 결과가 없어요.');
  }
  const scenarioName = String(d.scenarioName || '').trim() || DEFAULT_SCENARIO_NAME;
  if (scenarioName.length > SCENARIO_NAME_MAX) {
    throw apiError('VALIDATION', `시나리오 이름은 ${SCENARIO_NAME_MAX}자까지예요.`);
  }
  return {
    basePredictionId,
    scenarioName,
    income: incomeRequest(d),
    homelessHeadYn: homelessHeadYn(d),
    cardSpends: cardSpends(transactions, d.simulationSpend, getTaxYear(d)),
  };
}

/** 시뮬레이션 계산 (POST) → 응답 모양 검사 · 저장이 켜져 있으면 predictionId가 시나리오 번호예요 */
export async function simulate(d, transactions, basePredictionId, options = {}) {
  const taxYear = getTaxYear(d);
  checkTaxYear(taxYear);
  const body = await apiRequest(`/api/simulations/${taxYear}`, {
    ...options,
    method: 'POST',
    body: simulationRequest(d, transactions, basePredictionId),
    offlineMessage: '시뮬레이션 서버에 연결할 수 없어요.',
  });
  return parseCalculationResult(body);
}

/** 결과 이력 목록 (GET) · 최신순 · 없으면 빈 목록 */
export async function fetchPredictions(taxYear, options = {}) {
  checkTaxYear(taxYear);
  const rows = await apiRequest(`/api/predictions?year=${taxYear}`, {
    ...options,
    offlineMessage: '결과 이력 서버에 연결할 수 없어요.',
  });
  if (!Array.isArray(rows)) throw apiError('OFFLINE', '결과 이력을 불러올 수 없어요.');
  return rows.map((row) => ({
    predictionId: Number(row.predictionId),
    taxYear: Number(row.taxYear),
    scenarioName: String(row.scenarioName || ''),
    basePredictionId: Number.isInteger(row.basePredictionId) ? row.basePredictionId : null,
    expectedRefund: Number(row.expectedRefund),
    predictedAt: row.predictedAt ? String(row.predictedAt) : '',
  }));
}

/** 결과 하나 다시 열기 (GET) · 없거나 남의 것이면 code 'NOT_FOUND' 오류 */
export async function fetchPrediction(predictionId, options = {}) {
  if (!Number.isInteger(predictionId) || predictionId < 1) {
    throw apiError('VALIDATION', '결과 번호를 확인해주세요.');
  }
  const body = await apiRequest(`/api/predictions/${predictionId}`, {
    ...options,
    offlineMessage: '결과 이력 서버에 연결할 수 없어요.',
  });
  return parseCalculationResult(body);
}
