// back(Spring Boot) API 공통 호출 (소득 · 계산 연결용)
// 응답은 항상 공통 봉투예요 (팀 합의 11/09 · back ApiResponse)
//   성공: {"success": true,  "data": {...}, "meta": null, "error": null}        → data만 돌려줘요
//   실패: {"success": false, "data": null, "error": {"code", "message", "errorId"?}} → code · message를 가진 Error로 바꿔요
const env = import.meta.env || {};

// 로그인 토큰(JWT) 보관 이름 (localStorage · backend/docs/decisions/0004)
// 로그인 화면 연결 때 로그인 응답의 accessToken을 이 이름으로 저장 · 로그아웃 때 지워요
// [수정 11/17] 개발용 X-Member-Id 헤더(VITE_DEV_MEMBER_ID) → 로그인 토큰 · 서버가 토큰에서 회원번호를 꺼내요
export const ACCESS_TOKEN_KEY = 'hope-refund.accessToken';

/** 저장된 로그인 토큰 (없거나 저장소를 못 쓰면 null) */
export function readAccessToken() {
  try {
    return globalThis.localStorage?.getItem(ACCESS_TOKEN_KEY) || null;
  } catch {
    return null; // 사생활 보호 모드 등 저장소를 막은 브라우저
  }
}

// 서버에 연결할 수 없을 때(OFFLINE) 체험용 계산으로 대신 보여 줘도 되는지.
// 개발 서버(npm run dev)에서만 허용해요 → 백엔드 없이 화면만 띄워 개발할 수 있게.
// 빌드한 파일(배포본)에서는 허용하지 않아요 → 서버가 꺼졌을 때 가짜 숫자가 결과처럼 보이지 않게.
export const demoFallbackAllowed = Boolean(env.DEV);

/** 이 오류를 체험용 계산으로 대신해도 되는지 (연결 실패 + 개발 서버일 때만) */
export function canUseDemo(error, allowed = demoFallbackAllowed) {
  return error?.code === 'OFFLINE' && allowed;
}

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
 * - 로그인 토큰이 있으면 Authorization: Bearer 헤더로 보내요 (없으면 서버가 401 UNAUTHORIZED).
 */
export async function apiRequest(
  path,
  { method = 'GET', body, fetchImpl = globalThis.fetch, accessToken, offlineMessage } = {},
) {
  const token = accessToken ?? readAccessToken();
  const headers = { Accept: 'application/json' };
  // 토큰에 공백 · 줄바꿈이 있으면 헤더에 넣지 않아요 (깨진 값 · 헤더 끼워넣기 방지) → 서버가 401
  if (typeof token === 'string' && /^[\w.~+/=-]+$/.test(token)) {
    headers.Authorization = `Bearer ${token}`;
  }
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
