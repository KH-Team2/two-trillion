// back(Spring Boot) API 공통 호출 함수입니다. 모든 API 파일이 이 함수를 사용합니다.
// - JSON으로 보내고 JSON으로 받습니다.
// - 응답은 공통 봉투 {success, data, meta, error}입니다 (backend/docs/api-rules.md).
//   성공이면 data만 꺼내 돌려주고, 실패면 error {code, message}를 ApiError로 바꿔 던집니다 (화면은 message를 표시).
// - 서버에 연결하지 못했거나 응답이 JSON이 아니면 error: 'NETWORK_ERROR'로 구분합니다.

// - auth: true인 요청(로그아웃 등)만 저장된 로그인 토큰을 Authorization 헤더로 보냅니다.
//   그 요청이 401이면 토큰이 만료 · 무효인 것 → 로그인 정보를 지우고 화면에 알립니다 (api-client.js expireLogin).
//   로그인 요청은 auth가 없어서, 비밀번호가 틀린 401(LOGIN_FAILED)은 로그인 화면 오류로만 보입니다.
import { bearerHeader, expireLogin } from './api-client.js';

export const NETWORK_ERROR = 'NETWORK_ERROR';

export class ApiError extends Error {
  constructor(code, message, status, errorId) {
    super(message);
    this.name = 'ApiError';
    this.code = code; // 서버 오류 코드 (예: DUPLICATE_LOGIN_ID)
    this.status = status; // HTTP 상태 코드 (연결 실패는 0)
    this.errorId = errorId; // 서버 오류(500 · 503) 추적 번호 (없으면 undefined)
  }
}

const CONNECTION_MESSAGE = '서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.';

export async function apiRequest(
  path,
  { method = 'GET', body, auth = false } = {},
  fetchImpl = globalThis.fetch,
) {
  // 헤더는 await 전에 만듦 → 로그아웃처럼 호출 직후 토큰을 지워도 보낼 토큰은 이미 담겨 있음
  const headers = {
    ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
    ...(auth ? bearerHeader() : {}),
  };
  let response;
  try {
    response = await fetchImpl(path, {
      method,
      headers: Object.keys(headers).length ? headers : undefined,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(NETWORK_ERROR, CONNECTION_MESSAGE, 0);
  }

  let data = null;
  try {
    data = await response.json();
  } catch {
    data = null; // 응답이 JSON이 아님 (프록시 오류 · 서버 꺼짐 등)
  }

  // 봉투 모양이 아니면 (프록시 오류 · 서버 꺼짐 등) 연결 실패로 본다
  if (data === null || typeof data !== 'object' || typeof data.success !== 'boolean') {
    throw new ApiError(NETWORK_ERROR, CONNECTION_MESSAGE, response.status);
  }
  if (!response.ok || !data.success) {
    const error = data.error || {};
    if (response.status === 401 && headers.Authorization) expireLogin();
    throw new ApiError(
      error.code || NETWORK_ERROR,
      error.message || '요청을 처리하지 못했어요.',
      response.status,
      error.errorId,
    );
  }
  return data.data;
}
