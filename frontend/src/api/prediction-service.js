// 조건 변경 시뮬레이션 · 결과 이력 API 연결 (F-04-01 · F-04-02)
//   POST /api/simulations/{taxYear}  바꾼 조건으로 다시 계산 → 시나리오로 저장 (INCOME · EXPENSE는 그대로)
//   GET  /api/predictions?year=      현재(기준 결과) + 시나리오 목록
//   GET  /api/predictions/{id}       결과 하나 다시 열기 (항목별 내역 포함)
import { apiError, apiRequest, checkTaxYear } from './api-client.js';
import { parseCalculationResult } from './calculation-contract.js';
import { incomeRequest } from './income-service.js';
import { isCounted, payCode } from './expense-service.js';
import { categoryCodes } from './services.js';
import { getTaxYear } from '../utils/tax-year.js';

// 분류 코드(services.js categoryCodes와 같은 이름) → SPEND_CATEGORY.CATEGORY_ID
// 업로드 거래는 한글 이름(일반)이라 categoryCodes로 코드(GENERAL)로 바꾼 뒤 번호로, 가상 추가 사용액은 처음부터 코드
const CATEGORY_ID = { GENERAL: 1, TRANSIT: 2, MARKET: 3, CULTURE: 4, EXCLUDED: 5 };
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
 * - 업로드 거래: 그 연도 · 집계 포함(isCounted · 화면 합계 uploadTotals와 같은 기준)
 * - 가상 추가 사용액(simulationSpend): 0원보다 큰 줄만
 * - 기존 계산(back)이 EXPENSE를 분류 · 결제수단별로 더하는 것과 같은 모양이에요
 */
export function cardSpends(transactions = [], simulationSpend = [], taxYear) {
  const sums = new Map();
  const add = (category, payment, amount) => {
    const categoryId = CATEGORY_ID[categoryCodes[category] ?? category] ?? 1; // 분류를 모르면 일반 (업로드 화면 기본값과 같음)
    const payMethod = payCode(payment); // 결제수단 → CREDIT · CHECK · CASH (업로드 화면과 같은 변환)
    if (!payMethod || !Number.isFinite(amount)) return;
    const key = `${categoryId}|${payMethod}`;
    sums.set(key, (sums.get(key) || 0) + Math.round(amount));
  };
  for (const t of transactions) {
    if (!isCounted(t)) continue;
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
