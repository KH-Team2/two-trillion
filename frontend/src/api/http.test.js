// http.js 테스트: 정상 응답(봉투의 data) · 오류 응답(error.code) · 서버 오류 errorId · 서버 연결 실패 · JSON 아닌 응답
import assert from 'node:assert/strict';
import { apiRequest, ApiError, NETWORK_ERROR } from './http.js';

const reply = (status, body) => async () => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => {
    if (body === undefined) throw SyntaxError('not json');
    return body;
  },
});

// 정상 응답 → JSON 반환, 요청 본문은 JSON 문자열
let sent;
const ok = await apiRequest('/api/x', { method: 'POST', body: { a: 1 } }, async (url, init) => {
  sent = { url, init };
  return reply(200, { success: true, data: { id: 1 }, meta: null, error: null })();
});
assert.deepEqual(ok, { id: 1 });
assert.equal(sent.url, '/api/x');
assert.equal(sent.init.method, 'POST');
assert.equal(sent.init.body, '{"a":1}');
assert.equal(sent.init.headers['Content-Type'], 'application/json');

// GET은 본문 · Content-Type 없음
await apiRequest('/api/y', {}, async (url, init) => {
  assert.equal(init.method, 'GET');
  assert.equal(init.body, undefined);
  return reply(200, { success: true, data: null, meta: null, error: null })();
});

// 오류 응답 → ApiError(code, message, status)
await assert.rejects(
  apiRequest(
    '/api/x',
    {},
    reply(409, {
      success: false,
      data: null,
      meta: null,
      error: { code: 'DUPLICATE_LOGIN_ID', message: '이미 사용 중인 아이디예요' },
    }),
  ),
  (e) =>
    e instanceof ApiError &&
    e.code === 'DUPLICATE_LOGIN_ID' &&
    e.message === '이미 사용 중인 아이디예요' &&
    e.status === 409,
);

// 서버 오류(500) → SERVER_ERROR + errorId
await assert.rejects(
  apiRequest(
    '/api/x',
    {},
    reply(500, {
      success: false,
      data: null,
      meta: null,
      error: { code: 'SERVER_ERROR', message: '잠시 후 다시 시도해 주세요.', errorId: 'a3f9c2d1' },
    }),
  ),
  (e) => e.code === 'SERVER_ERROR' && e.errorId === 'a3f9c2d1' && e.status === 500,
);

// 봉투 모양이 아닌 200 응답 → NETWORK_ERROR
await assert.rejects(
  apiRequest('/api/x', {}, reply(200, { id: 1 })),
  (e) => e.code === NETWORK_ERROR,
);

// 서버 연결 실패 → NETWORK_ERROR
await assert.rejects(
  apiRequest('/api/x', {}, async () => {
    throw TypeError('Failed to fetch');
  }),
  (e) => e.code === NETWORK_ERROR && e.status === 0,
);

// JSON이 아닌 오류 응답(프록시 502 등) → NETWORK_ERROR
await assert.rejects(apiRequest('/api/x', {}, reply(502)), (e) => e.code === NETWORK_ERROR);

// 로그인 토큰 (F-01-02): auth 요청만 Authorization 헤더 · 그 요청이 401이면 로그인 정보 삭제 + 만료 알림
const store = { 'hope-refund.accessToken': 'header.payload.sig', 'hope-refund.loginMember': '{}' };
Object.defineProperty(globalThis, 'localStorage', {
  configurable: true,
  value: {
    getItem: (key) => store[key] ?? null,
    removeItem: (key) => delete store[key],
  },
});
let expiredEvents = 0;
globalThis.addEventListener?.('hope-refund:auth-expired', () => expiredEvents++);
try {
  let headers;
  const capture = (status, body) => async (url, init) => {
    headers = init.headers;
    return reply(status, body)();
  };
  const fail401 = {
    success: false,
    data: null,
    meta: null,
    error: { code: 'LOGIN_FAILED', message: '아이디 또는 비밀번호가 맞지 않아요' },
  };

  // auth 없는 요청(로그인 등)은 토큰을 보내지 않고, 401(비밀번호 틀림)이어도 저장된 토큰을 지우지 않음
  await assert.rejects(
    apiRequest('/api/auth/login', { method: 'POST', body: {} }, capture(401, fail401)),
    (e) => e.code === 'LOGIN_FAILED' && e.message === '아이디 또는 비밀번호가 맞지 않아요',
  );
  assert.equal(headers.Authorization, undefined);
  assert.equal(store['hope-refund.accessToken'], 'header.payload.sig');

  // auth 요청은 Bearer 토큰을 보냄
  await apiRequest(
    '/api/auth/logout',
    { method: 'POST', auth: true },
    capture(200, { success: true, data: null, meta: null, error: null }),
  );
  assert.equal(headers.Authorization, 'Bearer header.payload.sig');

  // auth 요청이 401 → 토큰 · 회원 정보 삭제
  await assert.rejects(
    apiRequest(
      '/api/auth/logout',
      { method: 'POST', auth: true },
      capture(401, { ...fail401, error: { code: 'UNAUTHORIZED', message: '로그인이 필요해요' } }),
    ),
    (e) => e.code === 'UNAUTHORIZED',
  );
  assert.equal(store['hope-refund.accessToken'], undefined);
  assert.equal(store['hope-refund.loginMember'], undefined);
  if (globalThis.addEventListener) assert.equal(expiredEvents, 1);
} finally {
  delete globalThis.localStorage;
}

console.log(
  'PASS: http.js 봉투 정상 · 오류 코드 · errorId · 연결 실패 · JSON 아닌 응답 · 로그인 토큰 · 401 만료',
);
