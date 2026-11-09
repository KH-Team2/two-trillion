// back(Spring Boot) API 공통 호출 (소득 · 계산 연결용)
// 응답은 항상 공통 봉투예요 (팀 합의 11/09 · back ApiResponse)
//   성공: {"success": true,  "data": {...}, "meta": null, "error": null}        → data만 돌려줘요
//   실패: {"success": false, "data": null, "error": {"code", "message", "errorId"?}} → code · message를 가진 Error로 바꿔요
const env = import.meta.env || {};
// 개발용 회원번호는 개발 서버(npm run dev)에서만 써요.
// 빌드한 파일에서는 보내지 않아요 → 배포 화면이 모두 같은 회원으로 요청하는 사고 방지
const devMemberId = env.DEV ? env.VITE_DEV_MEMBER_ID : undefined;

export function apiError(code, message, errorId) {
  // 서버 오류(500 · 503)엔 추적 번호가 와요 → 문구 뒤에 붙여서 사용자가 알려줄 수 있게
  const error = new Error(errorId ? `${message} (오류 번호 ${errorId})` : message);
  error.code = code;
  if (errorId) error.errorId = errorId;
  return error;
}

/**
 * back API 요청.
 * - 서버에 연결할 수 없거나 응답이 JSON이 아니면 code 'OFFLINE' 오류를 던져요.
 * - 성공이면 봉투 안의 data만 돌려줘요.
 * - 오류 응답이면 서버가 준 error.code · error.message를 그대로 써요 (스택 트레이스 등은 화면에 띄우지 않아요).
 */
export async function apiRequest(
  path,
  { method = 'GET', body, fetchImpl = globalThis.fetch, memberId, offlineMessage } = {},
) {
  const id = memberId ?? devMemberId;
  const headers = { Accept: 'application/json' };
  if (id && /^\d+$/.test(String(id))) headers['X-Member-Id'] = String(id);
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const offline = offlineMessage || '서버에 연결할 수 없어요.';

  let response;
  try {
    response = await fetchImpl(path, {
      method,
      headers,
      credentials: 'same-origin',
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw apiError('OFFLINE', offline);
  }
  let json = null;
  try {
    json = await response.json();
  } catch {
    // JSON이 아니면 서버가 꺼져 있거나(개발 프록시 오류) 다른 서버가 응답한 거예요
    throw apiError('OFFLINE', offline);
  }
  if (typeof json?.success !== 'boolean') {
    // 공통 봉투가 아니면 우리 back이 아닌 다른 서버가 응답한 거예요
    throw apiError('OFFLINE', offline);
  }
  if (!response.ok || !json.success) {
    const err = json.error || {};
    throw apiError(
      err.code || `HTTP_${response.status}`,
      err.message || '잠시 후 다시 시도해 주세요.',
      err.errorId,
    );
  }
  return json.data;
}

/** 귀속 연도 검사 (주소에 들어가는 값) */
export function checkTaxYear(taxYear) {
  if (!Number.isInteger(taxYear) || taxYear < 2000 || taxYear > 2100) {
    throw apiError('VALIDATION', '귀속 연도를 확인해주세요.');
  }
}
