// 회원가입(F-01-01) · 로그인(F-01-02) · 아이디 찾기 · 비밀번호 재설정(F-01-03) back API 호출 함수입니다.
// 화면 입력값 이름을 API 이름으로 바꾸는 일도 여기서 합니다 (화면 username → API loginId 등).
// TODO: 중복확인 · 이메일 인증 · 아이디 찾기 · 비밀번호 재설정 주소는 팀 API 명세에 없는 제안 주소입니다. 명세 추가 필요.
import { apiRequest } from './http.js';
import { consentVersions } from '../config/consent-versions.js';
import { ageOn } from './account-service.js';

// 이메일 인증을 가입 화면에 보여 주고 · 가입 전에 꼭 마치게 할지 [추가 11/17 · 수정 11/20 환경변수로]
// frontend/.env 의 VITE_EMAIL_VERIFICATION_REQUIRED=true 일 때만 켬 (없으면 false: 인증 UI 숨김 · 인증 없이 가입)
// 켤 때는 서버 backend/.env 의 EMAIL_VERIFICATION_REQUIRED=true 도 같이 (화면 · 서버 값이 같아야 함)
export const EMAIL_VERIFICATION_REQUIRED =
  (import.meta.env || {}).VITE_EMAIL_VERIFICATION_REQUIRED === 'true';

// 화면 성별 버튼 값 → API·DB 값 (MEMBER.GENDER M/F)
export const GENDER_CODE = { 남성: 'M', 여성: 'F' };

// 가입 버튼을 누르기 전 화면 검사: 문제가 있으면 안내 문구, 없으면 ''를 돌려줍니다.
// 서버도 같은 항목을 다시 검사합니다 (화면 검사만으로 가입되지 않음).
export function signupFormError(
  form,
  now = new Date(),
  { requireVerification = EMAIL_VERIFICATION_REQUIRED } = {},
) {
  if (!form.username.trim()) return '아이디를 입력해 주세요';
  if (form.checkedUsername !== form.username) return '아이디 중복확인을 해주세요';
  if (form.password.length < 8) return '비밀번호는 8자 이상 입력해 주세요';
  if (form.password !== form.confirm) return '비밀번호 확인이 일치하지 않아요';
  if (!form.email.trim()) return '이메일을 입력해 주세요';
  if (form.checkedEmail !== form.email) return '이메일 중복확인을 해주세요';
  if (requireVerification && !form.verificationId) return '이메일 인증을 완료해 주세요';
  if (!form.birth) return '생년월일을 입력해 주세요';
  try {
    if (ageOn(form.birth, now) < 14) return '만 14세 이상만 가입할 수 있어요';
  } catch {
    return '생년월일을 확인해 주세요';
  }
  if (!GENDER_CODE[form.gender]) return '성별을 선택해 주세요';
  if (!form.terms) return '이용약관에 동의해야 가입할 수 있어요';
  if (!form.privacy) return '개인정보 수집·이용에 동의해야 가입할 수 있어요';
  if (!form.financial) return '소득·카드 내역 저장에 동의해야 가입할 수 있어요';
  return '';
}

// 화면 입력값 → 회원가입 API 요청 본문
export function toSignupRequest(form, versions = consentVersions) {
  return {
    loginId: form.username.trim(),
    email: form.email.trim(),
    password: form.password,
    passwordConfirm: form.confirm,
    birthDate: form.birth, // <input type="date"> 값 yyyy-MM-dd
    gender: GENDER_CODE[form.gender] || '',
    termsAgreed: Boolean(form.terms),
    privacyAgreed: Boolean(form.privacy),
    financeAgreed: Boolean(form.financial),
    termsVersion: versions.TERMS_VERSION,
    privacyVersion: versions.PRIVACY_VERSION,
    financeVersion: versions.FINANCE_VERSION,
    verificationId: form.verificationId || null, // 인증을 끈 동안은 null
  };
}

// 비밀번호 재설정(F-01-03) 버튼을 누르기 전 화면 검사: 문제가 있으면 안내 문구, 없으면 ''
// 서버도 같은 항목을 다시 검사합니다.
export function passwordResetFormError(form) {
  if (!form.email.trim()) return '이메일을 입력해 주세요';
  if (!/^\d{6}$/.test(form.code)) return '6자리 인증 코드를 입력해 주세요';
  if (form.newPassword.length < 8) return '비밀번호는 8자 이상 입력해 주세요';
  if (form.newPassword !== form.newPasswordConfirm) return '비밀번호 확인이 일치하지 않아요';
  return '';
}

export function createMemberApi(request = apiRequest) {
  return {
    // 아이디 중복확인 → {available, message}
    checkLoginId: (loginId) =>
      request(`/api/auth/members/availability?loginId=${encodeURIComponent(loginId.trim())}`),
    // 이메일 중복확인 → {available, message}
    checkEmail: (email) =>
      request(`/api/auth/members/availability?email=${encodeURIComponent(email.trim())}`),
    // 인증번호 발송 → {verificationId, expiresAt, devCode}
    sendEmailCode: (email) =>
      request('/api/auth/email-verifications', { method: 'POST', body: { email: email.trim() } }),
    // 인증번호 확인 → {verified, message}
    confirmEmailCode: (verificationId, code) =>
      request(`/api/auth/email-verifications/${encodeURIComponent(verificationId)}/confirmation`, {
        method: 'POST',
        body: { code },
      }),
    // 회원가입 → {memberId, loginId, email, name, role, createdAt}
    signUp: (form) => request('/api/auth/members', { method: 'POST', body: toSignupRequest(form) }),
    // 로그인(F-01-02) → {accessToken, tokenType: 'Bearer', expiresAt, loginId, role}
    // 실패: 401 LOGIN_FAILED · WITHDRAWN_MEMBER · LOGIN_LOCKED (서버 문구를 그대로 화면에 표시)
    login: (loginId, password) =>
      request('/api/auth/login', { method: 'POST', body: { loginId: loginId.trim(), password } }),
    // 로그아웃 → null · 지금 토큰을 서버에서 무효 처리 (토큰이 필요한 요청)
    logout: () => request('/api/auth/logout', { method: 'POST', auth: true }),
    // 아이디 찾기(F-01-03) → {maskedLoginId} (예: te******) · 가입 정보가 없으면 404 NOT_FOUND
    // 이메일은 주소에 남지 않게 본문으로 보냄
    findLoginId: (email) =>
      request('/api/auth/login-id-lookups', { method: 'POST', body: { email: email.trim() } }),
    // 비밀번호 재설정 인증 코드 발송(F-01-03) → {expiresAt, devCode} · 코드는 10분 유효
    sendPasswordResetCode: (email) =>
      request('/api/auth/password-resets', { method: 'POST', body: { email: email.trim() } }),
    // 코드 확인 + 새 비밀번호 저장(F-01-03) → null
    // 실패: 400 RESET_CODE_FAILED(틀림) · RESET_CODE_EXPIRED · RESET_CODE_LIMIT(만료 · 5번 틀림 → 다시 받기)
    completePasswordReset: (form) =>
      request('/api/auth/password-resets/completion', {
        method: 'POST',
        body: {
          email: form.email.trim(),
          code: form.code,
          newPassword: form.newPassword,
          newPasswordConfirm: form.newPasswordConfirm,
        },
      }),
  };
}

export const memberApi = createMemberApi();
