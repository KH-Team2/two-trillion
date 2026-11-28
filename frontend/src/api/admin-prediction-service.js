// 관리자 예측 결과 조회 API 연결 (F-08-02 ② · S-08) · [신규 11/26 고은]
// - GET /api/admin/predictions?keyword=&taxYear=&kind=&page=&size=
//   전체 회원의 예상 세액 계산 결과 한 쪽 (+ 쪽 정보 meta) · 최신순 · 이메일은 일부 가림
//   관리자(ROLE=ADMIN) 토큰만 통과 (아니면 403) · 조회할 때마다 서버가 ACCESS_LOG에 기록해요
// - 보기만 하는 기능이라 수정 · 삭제 함수는 없어요
import { apiError, apiRequest } from './api-client.js';

// 종류 검색값 → 화면 이름 · CURRENT = 처음 계산(시나리오 이름 「현재」) · SCENARIO = 조건 변경 시뮬레이션
export const KIND_LABEL = { CURRENT: '현재 결과', SCENARIO: '시나리오' };

// 한 쪽에 보여 줄 건수 · 서버 최대값(@Max(100))
export const PREDICTION_PAGE_SIZE = 20;
const MAX_SIZE = 100;
// 귀속 연도 범위 · 서버 검사(@Min(2000) @Max(2100))와 같게
const MIN_YEAR = 2000;
const MAX_YEAR = 2100;

const OFFLINE_MESSAGE = '관리자 서버에 연결할 수 없어요.';

/**
 * 화면 검색 조건 → 주소 뒤에 붙일 글자(?keyword=…&page=1&size=20)
 * - 비어 있는 조건은 빼요 (서버가 「조건 없음 = 전체」로 처리)
 * - URLSearchParams: & = 같은 글자가 들어가도 주소가 망가지지 않게 바꿔 줘요
 * - 서버가 400으로 거절할 값은 보내기 전에 막아요 (같은 문구를 화면에 바로 보여 주려고)
 */
export function predictionQuery({
  keyword = '',
  taxYear = '',
  kind = '',
  page = 1,
  size = PREDICTION_PAGE_SIZE,
} = {}) {
  const word = String(keyword ?? '').trim();
  const yearText = String(taxYear ?? '').trim();
  if (yearText) {
    const year = Number(yearText);
    if (!Number.isInteger(year) || year < MIN_YEAR || year > MAX_YEAR) {
      throw apiError('VALIDATION', `귀속 연도는 ${MIN_YEAR}~${MAX_YEAR} 사이 숫자로 써 주세요`);
    }
  }
  if (kind && !KIND_LABEL[kind]) throw apiError('VALIDATION', '결과 종류를 확인해 주세요');
  const pageNo = Number(page);
  const sizeNo = Number(size);
  if (!Number.isInteger(pageNo) || pageNo < 1)
    throw apiError('VALIDATION', '쪽 번호를 확인해 주세요');
  if (!Number.isInteger(sizeNo) || sizeNo < 1 || sizeNo > MAX_SIZE) {
    throw apiError('VALIDATION', `한 쪽에 1~${MAX_SIZE}건까지 볼 수 있어요`);
  }
  const params = new URLSearchParams();
  if (word) params.set('keyword', word);
  if (yearText) params.set('taxYear', String(Number(yearText)));
  if (kind) params.set('kind', kind);
  params.set('page', String(pageNo));
  params.set('size', String(sizeNo));
  return `?${params.toString()}`;
}

/** 예측 결과 한 쪽 + 쪽 정보 · 비어 있으면 rows는 빈 배열 */
export async function fetchPredictions(filters = {}, options = {}) {
  const { data, meta } = await apiRequest(`/api/admin/predictions${predictionQuery(filters)}`, {
    ...options,
    withMeta: true, // 응답의 meta(쪽 정보)까지 받기 (api-client.js 옵션)
    offlineMessage: OFFLINE_MESSAGE,
  });
  return {
    rows: Array.isArray(data) ? data : [],
    meta: meta ?? {
      page: Number(filters.page) || 1,
      size: PREDICTION_PAGE_SIZE,
      total: 0,
      totalPages: 1,
    },
  };
}

/** 시나리오 이름이 「현재」면 처음 계산 · 아니면 조건 변경 시뮬레이션 (서버 검색 규칙과 같게) */
export function kindOf(row) {
  return row?.scenarioName === '현재' ? 'CURRENT' : 'SCENARIO';
}

/**
 * 예상 환급액 → 화면 글자 · 음수면 「추가 납부」(명세 F-06-01 비고와 같은 표현)
 * 예) 120000 → '120,000원 환급' · -30000 → '30,000원 추가 납부' · null → '-'
 */
export function refundText(value) {
  if (value === null || value === undefined || value === '') return '-';
  const n = Number(value);
  if (!Number.isFinite(n)) return '-';
  if (n < 0) return `${Math.abs(n).toLocaleString('ko-KR')}원 추가 납부`;
  return `${n.toLocaleString('ko-KR')}원 환급`;
}

/** 금액 → '1,234,000원' · 비어 있으면 '-' */
export function wonText(value) {
  if (value === null || value === undefined || value === '') return '-';
  const n = Number(value);
  return Number.isFinite(n) ? `${n.toLocaleString('ko-KR')}원` : '-';
}
