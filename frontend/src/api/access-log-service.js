// 관리자 접근 기록 API 연결 (F-21 · F-08-02 ③ · S-08) · [신규 11/23]
// - GET /api/admin/access-logs?startDate=&endDate=&ipAddress=&page=&size=
//   관리자(ROLE=ADMIN) 토큰만 통과 (아니면 403) · 조회할 때마다 서버가 「누가 봤는지」를 ACCESS_LOG에 남겨요
// - 응답: data = 기록 목록, meta = { page, size, total, totalPages } (팀 공통 PageMeta · 페이지는 1부터)
import { apiError, apiRequest } from './api-client.js';

// 결과 코드 → 화면 이름 (DB CK_ACCESS_LOG_RESULT와 같은 값)
export const ACCESS_RESULT_LABEL = { SUCCESS: '성공', FAIL: '실패', DENIED: '권한 없음' };

// 한 쪽에 보여 줄 기록 수 · 서버 최대값(@Max(100))보다 크면 400이라 화면에서도 같은 범위만 허용
export const PAGE_SIZE = 20;
export const MAX_SIZE = 100;

const OFFLINE_MESSAGE = '관리자 서버에 연결할 수 없어요.';
// 'YYYY-MM-DD' 모양인지 (예: 2026-11-23) · <input type="date">가 주는 모양과 같아요
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

/**
 * 화면 검색 조건 → 주소 뒤에 붙일 글자(?startDate=…&page=1&size=20)
 * - 비어 있는 조건은 빼요 (서버가 「조건 없음 = 전체」로 처리)
 * - 날짜 모양이 틀리거나, 시작일이 종료일보다 늦으면 서버에 보내기 전에 막아요
 * URLSearchParams: 주소에 넣을 값을 안전하게 바꿔 주는 도구 (띄어쓰기 · & 같은 글자가 주소를 망가뜨리지 않게)
 */
export function accessLogQuery({
  startDate = '',
  endDate = '',
  ipAddress = '',
  page = 1,
  size = PAGE_SIZE,
} = {}) {
  const start = String(startDate ?? '').trim();
  const end = String(endDate ?? '').trim();
  const ip = String(ipAddress ?? '').trim();
  if (start && !DATE_PATTERN.test(start)) throw apiError('VALIDATION', '시작일을 확인해 주세요');
  if (end && !DATE_PATTERN.test(end)) throw apiError('VALIDATION', '종료일을 확인해 주세요');
  // 같은 모양(YYYY-MM-DD)의 글자는 글자 순서 비교가 곧 날짜 순서 비교예요
  if (start && end && start > end) {
    throw apiError('VALIDATION', '시작일이 종료일보다 늦어요');
  }
  const pageNo = Number(page);
  const sizeNo = Number(size);
  if (!Number.isInteger(pageNo) || pageNo < 1)
    throw apiError('VALIDATION', '쪽 번호를 확인해 주세요');
  if (!Number.isInteger(sizeNo) || sizeNo < 1 || sizeNo > MAX_SIZE) {
    throw apiError('VALIDATION', `한 쪽에 1~${MAX_SIZE}건까지 볼 수 있어요`);
  }
  const params = new URLSearchParams();
  if (start) params.set('startDate', start);
  if (end) params.set('endDate', end);
  if (ip) params.set('ipAddress', ip);
  params.set('page', String(pageNo));
  params.set('size', String(sizeNo));
  return `?${params.toString()}`;
}

/** 접근 기록 한 쪽 + 페이지 정보 · 비어 있으면 rows는 빈 배열 */
export async function fetchAccessLogs(filters = {}, options = {}) {
  const { data, meta } = await apiRequest(`/api/admin/access-logs${accessLogQuery(filters)}`, {
    ...options,
    withMeta: true,
    offlineMessage: OFFLINE_MESSAGE,
  });
  return {
    rows: Array.isArray(data) ? data : [],
    // meta가 없으면(예전 서버) 지금 쪽 하나뿐인 것으로 봐요
    meta: meta ?? { page: Number(filters.page) || 1, size: PAGE_SIZE, total: 0, totalPages: 1 },
  };
}
