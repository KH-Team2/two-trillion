import { ACCESS_TOKEN_KEY, LOGIN_MEMBER_KEY, clearLogin, readAccessToken } from './api-client.js';
import { memberApi } from './member-api.js';
// 계정 어댑터입니다.
// - 로그인 · 로그아웃(F-01-02): Spring Boot 서버 (아래 accountService.login · logout · current) · 토큰은 localStorage
// - 회원 탈퇴(F-01-05): 서버 POST /api/members/me/withdrawal (아래 accountService.withdraw)
// - 회원정보 조회 · 수정 · 비밀번호 변경(F-01-04) · 아이디 찾기 · 비밀번호 재설정(F-01-03)은 화면에서 memberApi를 바로 불러요
// [수정 11/25] 브라우저 체험용 계정 전부 삭제 · 이유: 체험판 기능 삭제 결정
//   (createAccountService · LOCK_MS · ACCOUNT_KEY · 빠른 체험(enterDemo · isDemo · demoStorage) · 브라우저 회원가입 ·
//    관리자 회원 관리 · 성별 수정 · 브라우저 비밀번호 변경 · 브라우저 탈퇴) → 서버 API만 남김
export const SESSION_KEY = 'hope-session-v1';
// 화면에서만 쓰는 무작위 id (계산 이력 줄 구분용)
export function newPrototypeId() {
  return Array.from(crypto.getRandomValues(new Uint8Array(16)))
    .map((n) => n.toString(16).padStart(2, '0'))
    .join('');
}
export function ageOn(birth, now) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(birth);
  if (!match) throw Error('올바른 생년월일을 입력해주세요.');
  const y = Number(match[1]),
    m = Number(match[2]),
    d = Number(match[3]),
    date = new Date(Date.UTC(y, m - 1, d));
  if (
    y < 1900 ||
    date.getUTCFullYear() !== y ||
    date.getUTCMonth() !== m - 1 ||
    date.getUTCDate() !== d
  )
    throw Error('올바른 생년월일을 입력해주세요.');
  const korea = new Date(now.getTime() + 9 * 60 * 60 * 1000),
    cy = korea.getUTCFullYear(),
    cm = korea.getUTCMonth() + 1,
    cd = korea.getUTCDate();
  if (y > cy || (y === cy && (m > cm || (m === cm && d > cd))))
    throw Error('미래의 생년월일은 입력할 수 없습니다.');
  return cy - y - (cm < m || (cm === m && cd < d) ? 1 : 0);
}
// 서버 로그인 응답의 만료 시각(한국 시각 · 시간대 표시 없음) → 밀리초
function expiresAtMs(value) {
  const text = String(value || '');
  return Date.parse(/(Z|[+-]\d\d:\d\d)$/i.test(text) ? text : `${text}+09:00`);
}
// 서버에 로그인한 회원 (토큰과 회원 정보가 둘 다 있고 만료 전일 때만)
function serverMember() {
  try {
    const member = JSON.parse(localStorage.getItem(LOGIN_MEMBER_KEY) || 'null');
    if (!member?.loginId || !localStorage.getItem(ACCESS_TOKEN_KEY)) return null;
    if (!(expiresAtMs(member.expiresAt) > Date.now())) {
      clearLogin(); // 만료된 토큰 → 다시 로그인
      return null;
    }
    const id = `member:${member.loginId}`;
    // 화면 입력 · 기록 저장 칸을 회원별로 나눔 (services.js scopedKey가 이 값을 씀)
    if (sessionStorage.getItem(SESSION_KEY) !== id) sessionStorage.setItem(SESSION_KEY, id);
    // 서버 로그인 응답에는 아이디 · 권한만 있음 (이메일 · 성별 · 생년월일은 서버가 회원번호로 조회해서 씀)
    return { id, username: member.loginId, role: member.role, status: 'ACTIVE', server: true };
  } catch {
    return null;
  }
}
export const accountService = {
  // 지금 로그인한 회원 (없으면 null → 화면은 로그인 · 회원가입만 보여 줘요)
  current: () => {
    if (typeof window === 'undefined') return null;
    return serverMember();
  },
  // 로그인(F-01-02): 서버 POST /api/auth/login → 토큰 · 회원 정보 저장 → 로그인한 회원 반환
  // 실패하면 서버 오류(ApiError: code · message)를 그대로 던짐 → 화면이 message 표시
  login: async (loginId, password) => {
    const result = await memberApi.login(loginId, password);
    localStorage.setItem(ACCESS_TOKEN_KEY, result.accessToken);
    localStorage.setItem(
      LOGIN_MEMBER_KEY,
      JSON.stringify({ loginId: result.loginId, role: result.role, expiresAt: result.expiresAt }),
    );
    return serverMember();
  },
  // 로그아웃: 서버에 토큰 무효 요청(POST /api/auth/logout) 후 저장값 삭제
  // 서버 요청이 실패해도(서버 꺼짐 등) 화면은 로그아웃 · 요청 헤더는 호출 순간에 담기므로 바로 지워도 됨
  logout: () => {
    if (readAccessToken()) {
      memberApi.logout().catch(() => {});
    }
    clearLogin();
    sessionStorage.removeItem(SESSION_KEY);
  },
  // 회원 탈퇴(F-01-05): 서버 POST /api/members/me/withdrawal → 성공하면 저장된 로그인 정보 삭제
  // 서버가 토큰을 이미 무효로 했으므로 로그아웃 API는 따로 부르지 않음 · 실패하면 서버 문구를 그대로 던짐 (로그인 유지)
  // [수정 11/25] 빠른 체험 · 브라우저 탈퇴 분기 삭제 · 이유: 체험판 기능 삭제 (서버 회원만 있음)
  withdraw: async (password) => {
    await memberApi.withdrawMe(password);
    clearLogin();
    sessionStorage.removeItem(SESSION_KEY);
  },
};
