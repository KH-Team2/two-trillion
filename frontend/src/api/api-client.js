// back(Spring Boot) API 공통 호출 (소득 · 계산 연결용)
// 응답은 항상 공통 봉투예요 (팀 합의 11/09 · back ApiResponse)
//   성공: {"success": true,  "data": {...}, "meta": null, "error": null}        → data만 돌려줘요
//   실패: {"success": false, "data": null, "error": {"code", "message", "errorId"?}} → code · message를 가진 Error로 바꿔요
const env = import.meta.env || {};

// 로그인 토큰(JWT) 보관 이름 (localStorage · backend/docs/decisions/0004)
// 로그인 화면 연결 때 로그인 응답의 accessToken을 이 이름으로 저장 · 로그아웃 때 지워요
// [수정 11/17] 개발용 X-Member-Id 헤더(VITE_DEV_MEMBER_ID) → 로그인 토큰 · 서버가 토큰에서 회원번호를 꺼내요
export const ACCESS_TOKEN_KEY = 'hope-refund.accessToken';

// 로그인한 회원 정보 보관 이름 (로그인 응답의 loginId · role · expiresAt · 비밀번호 등은 넣지 않음)
export const LOGIN_MEMBER_KEY = 'hope-refund.loginMember';
// 토큰이 만료 · 로그아웃되어 서버가 401을 주면 보내는 화면 알림 (App이 로그인 화면으로 이동)
export const AUTH_EXPIRED_EVENT = 'hope-refund:auth-expired';

/** 저장된 로그인 토큰 (없거나 저장소를 못 쓰면 null) */
export function readAccessToken() {
  try {
    return globalThis.localStorage?.getItem(ACCESS_TOKEN_KEY) || null;
  } catch {
    return null; // 사생활 보호 모드 등 저장소를 막은 브라우저
  }
}

/** 토큰을 Authorization 헤더 모양으로 (토큰이 없거나 공백 · 줄바꿈이 있으면 빈 객체 → 헤더 끼워넣기 방지) */
export function bearerHeader(token = readAccessToken()) {
  return typeof token === 'string' && /^[\w.~+/=-]+$/.test(token)
    ? { Authorization: `Bearer ${token}` }
    : {};
}

/** 저장된 로그인 정보(토큰 · 회원 정보) 지우기 */
export function clearLogin() {
  try {
    globalThis.localStorage?.removeItem(ACCESS_TOKEN_KEY);
    globalThis.localStorage?.removeItem(LOGIN_MEMBER_KEY);
  } catch {
    // 저장소를 못 쓰는 브라우저 → 지울 것도 없음
  }
}

/** 보낸 토큰이 거절됨(401) → 로그인 정보를 지우고 화면에 알림 */
export function expireLogin() {
  clearLogin();
  if (typeof globalThis.dispatchEvent === 'function') {
    globalThis.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));
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
  // 토큰에 공백 · 줄바꿈이 있으면 헤더에 넣지 않아요 (깨진 값 · 헤더 끼워넣기 방지) → 서버가 401
  const headers = { Accept: 'application/json', ...bearerHeader(accessToken ?? readAccessToken()) };
  // [수정 11/17 고은] 파일(FormData)이면 JSON으로 바꾸지 않고 그대로 보냄
  // 이유: 카드 파일 업로드(POST /api/card-uploads)는 multipart 요청이라 JSON으로 보낼 수 없음
  //   · FormData일 때는 Content-Type을 직접 넣지 않음 → 브라우저가 파일 경계값(boundary)까지 넣어 줌
  const isForm = typeof FormData !== 'undefined' && body instanceof FormData;
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json';
  const offline = offlineMessage || '서버에 연결할 수 없어요.';

  let response;
  try {
    response = await fetchImpl(path, {
      method,
      headers,
      credentials: 'same-origin',
      // [수정 11/17 고은] FormData는 그대로 · 나머지는 지금처럼 JSON 글자로 바꿈
      body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
    });
  } catch {
    throw apiError('OFFLINE', offline);
  }
  // [수정 11/17 고은] 204(성공 · 본문 없음)면 JSON을 읽지 않고 바로 null을 돌려줌
  // 이유: 업로드 삭제(DELETE /api/card-uploads/{id})는 성공하면 204로 빈 응답을 줌
  //   ([수정 11/23 고은] 지금은 팀 규칙대로 200으로 바뀌었지만, 혹시 204가 오면 오류로 잘못 처리하지 않도록 그대로 둠)
  //   → 그대로 JSON을 읽으면 실패해서 아래에서 「서버에 연결할 수 없어요(OFFLINE)」로 잘못 처리됨
  if (response.status === 204) return null;
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
    // 토큰을 보냈는데 401 → 만료 · 로그아웃된 토큰 → 로그인 화면으로 (토큰 없이 보낸 체험 요청은 그대로 오류만)
    if (response.status === 401 && headers.Authorization) expireLogin();
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
