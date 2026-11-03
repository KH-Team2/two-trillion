// back(Spring Boot) API 공통 호출 (소득 · 계산 연결용)
// 오류 응답은 항상 {"error": "코드", "message": "화면 문구"} → code · message를 가진 Error로 바꿔요.
const env = import.meta.env || {};
// 개발용 회원번호는 개발 서버(npm run dev)에서만 써요.
// 빌드한 파일에서는 보내지 않아요 → 배포 화면이 모두 같은 회원으로 요청하는 사고 방지
const devMemberId = env.DEV ? env.VITE_DEV_MEMBER_ID : undefined;

export function apiError(code, message) {
  const error = new Error(message);
  error.code = code;
  return error;
}

/**
 * back API 요청.
 * - 서버에 연결할 수 없거나 응답이 JSON이 아니면 code 'OFFLINE' 오류를 던져요.
 * - 오류 응답이면 서버가 준 error · message를 그대로 써요 (스택 트레이스 등은 화면에 띄우지 않아요).
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
  if (!response.ok) {
    throw apiError(
      json?.error || `HTTP_${response.status}`,
      json?.message || '잠시 후 다시 시도해 주세요.',
    );
  }
  return json;
}

/** 귀속 연도 검사 (주소에 들어가는 값) */
export function checkTaxYear(taxYear) {
  if (!Number.isInteger(taxYear) || taxYear < 2000 || taxYear > 2100) {
    throw apiError('VALIDATION', '귀속 연도를 확인해주세요.');
  }
}
