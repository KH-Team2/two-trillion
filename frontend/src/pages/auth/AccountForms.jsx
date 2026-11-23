// 회원가입(F-01-01) · 로그인(F-01-02) 화면입니다. 둘 다 Spring Boot API에 연결되어 있습니다
// (memberApi · accountService.login). 관리자 권한은 DB(MEMBER.ROLE = ADMIN)에서 지정합니다.
// [수정 11/23] 체험용 코드 삭제 · 이유: 체험판 기능 삭제 결정
//   - 브라우저 회원가입 · 로그인 · 체험 인증번호 · 최초 관리자 설정(setup) · 「브라우저 시연」 안내 삭제 → 서버 API만 사용
//   - MemberManagement(관리자 회원 관리) · WithdrawAccount(탈퇴) 삭제 → 서버 API 전이라 각 화면은 「준비 중」 안내
//   - 비밀번호 재설정 화면(reset) → 「준비 중」 안내
//   - recordAccess(브라우저 접근 기록) 삭제 → 로그인 기록은 서버(ACCESS_LOG)가 남겨요
//   - demoCode → devCode 이름 변경 (서버가 개발 환경에서만 보내 주는 인증번호라서)
import React, { useState, useEffect, useRef } from 'react';
import { accountService } from '@/api/account-service.js';
import { EMAIL_VERIFICATION_REQUIRED, memberApi, signupFormError } from '@/api/member-api.js';
import ComingSoon from '@/components/common/ComingSoon.jsx';
// 「비밀번호를 잊으셨나요?」 버튼 표시 여부 [추가 11/17] · 비밀번호 재설정 서버 API가 생기면 true
const SHOW_PASSWORD_RESET = false;
const agreements = [
  [
    'terms',
    '[필수] 서비스 이용약관 동의',
    '희망환급은 소득·지출 입력, 예상 결과 조회, 시뮬레이션 및 안내를 제공합니다. 만 14세 이상 가입할 수 있으며 관리자는 기존 계정에 권한을 부여합니다. 본인에게 사용 권한이 있는 자료만 등록해야 합니다. 계산·분류 결과는 입력값 기준 예상치이며 실제 신고·자격 확정·세무 자문이 아닙니다. 마이페이지에서 탈퇴할 수 있으며 마지막 관리자는 인계가 필요합니다. 운영 주체·시행일·분쟁 처리 등은 서비스 정책 확정 전 보완해야 합니다.',
  ],
  [
    'privacy',
    '[필수] 개인정보 수집·이용 동의',
    '목적: 계정 식별, 로그인, 연령 확인, 그룹 비교 및 서비스 지원. 항목: 아이디, 이메일, 비밀번호 파생 해시, 생년월일, 성별, 동의 이력. 보유기간·보존 정책은 운영 검토가 필요합니다. 탈퇴 후 30일 유예기간 동안 아이디·이메일을 유지하고 이후 식별 정보 처리 예정입니다. 회원가입 정보는 희망환급 서버(DB)에 저장합니다. 주민등록번호 전체는 수집하지 않습니다. 필수 동의 거부 시 가입할 수 없습니다. 운영 주체·문의처·법적 보존 대상 및 기간·위탁 정책은 확정 전입니다.',
  ],
  [
    'financial',
    '[필수] 금융 정보 저장 동의',
    '목적: 연말정산 예상 계산, 거래 자동 분류, 결과 조회 및 시뮬레이션. 저장 항목: 사용자가 제출한 급여·기납부세액·결제수단별 지출액, 거래일·상호명·금액·결제수단, 파일 처리 메타정보, 가족 수·월세·주거 및 감면 자격 입력, 계산·시뮬레이션 기록. 원본 CSV·XLSX 파일 자체와 카드번호 전체·계좌 비밀번호는 저장하지 않습니다. 탈퇴 요청 시 즉시 삭제하지 않으며 30일 유예 및 이후 식별 정보 처리 정책은 운영 검토가 필요합니다. 업로드 파일별 삭제도 가능합니다. 거부 시 가입할 수 없습니다. 실제 금융기관·국세청 조회는 하지 않습니다.',
  ],
];
export function AccountForms({ page, go, onLogin }) {
  const [error, setError] = useState(''),
    [busy, setBusy] = useState(false);
  const [email, setEmail] = useState(''),
    [username, setUsername] = useState(''),
    [checkedEmail, setCheckedEmail] = useState(''),
    [checkedUsername, setCheckedUsername] = useState(''),
    [gender, setGender] = useState('');
  const [password, setPassword] = useState(''),
    [confirm, setConfirm] = useState('');
  const [code, setCode] = useState(''),
    [devCode, setDevCode] = useState(''),
    [expiresAt, setExpiresAt] = useState(0),
    [now, setNow] = useState(Date.now()),
    [proof, setProof] = useState(''),
    [verificationMessage, setVerificationMessage] = useState(''),
    [pendingId, setPendingId] = useState(''),
    [success, setSuccess] = useState('');
  const submitting = useRef(false); // 가입 요청 중복 전송 방지
  const [consent, setConsent] = useState({
    terms: false,
    privacy: false,
    financial: false,
  });
  // 회원가입 화면이면 true, 로그인 화면이면 false (둘 다 서버 API로 처리해요)
  const signup = page === 'signup';
  useEffect(() => {
    if (!expiresAt) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [expiresAt]);
  const expired = expiresAt > 0 && now >= expiresAt;
  // 서버 중복확인: 사용 불가면 서버 문구를 표시
  const check = async (kind) => {
    try {
      const value = kind === 'username' ? username : email;
      if (!value.trim())
        throw Error(kind === 'username' ? '아이디를 입력해 주세요' : '이메일을 입력해 주세요');
      const result =
        kind === 'username'
          ? await memberApi.checkLoginId(value)
          : await memberApi.checkEmail(value);
      if (!result.available) throw Error(result.message);
      if (kind === 'username') setCheckedUsername(value);
      else setCheckedEmail(value);
      setError('');
    } catch (err) {
      setError(err.message);
    }
  };
  // 비밀번호 재설정: 서버 API가 아직 없어 「준비 중」 안내만 보여 줘요
  if (page === 'reset')
    return (
      <ComingSoon title="아이디 찾기 · 비밀번호 재설정">
        아이디 찾기와 비밀번호 재설정은 서버 연결 작업 중이에요.
      </ComingSoon>
    );
  return (
    <section className="card auth-card" key={page}>
      <h2>{signup ? '희망환급 회원가입' : '다시 만나 반가워요'}</h2>
      <p className="muted">
        {signup
          ? '입력한 회원 정보는 희망환급 서버에 저장됩니다. 비밀번호는 암호화하여 저장하며, 실명은 받지 않습니다.'
          : '희망환급에 가입한 아이디로 로그인해요. 비밀번호를 5번 틀리면 10분 동안 로그인할 수 없어요.'}
      </p>
      {success && (
        <p className="notice" role="status">
          {success}
        </p>
      )}
      <form
        noValidate
        onSubmit={async (e) => {
          e.preventDefault();
          if (submitting.current) return;
          setError('');
          setSuccess('');
          const f = new FormData(e.currentTarget);
          if (signup) {
            // 서버 회원가입: 화면 검사 → POST /api/auth/members → 로그인 화면 이동
            const form = {
              username,
              checkedUsername,
              password,
              confirm,
              email,
              checkedEmail,
              verificationId: proof,
              birth: String(f.get('birth') || ''),
              gender,
              ...consent,
            };
            const message = signupFormError(form);
            if (message) {
              setError(message);
              return;
            }
            submitting.current = true;
            setBusy(true);
            try {
              await memberApi.signUp(form);
              setConsent({ terms: false, privacy: false, financial: false });
              setProof('');
              setPendingId('');
              setDevCode('');
              setExpiresAt(0);
              setCode('');
              setVerificationMessage('');
              setPassword('');
              setConfirm('');
              setCheckedUsername('');
              setCheckedEmail('');
              setUsername('');
              setGender('');
              go('login');
              setSuccess('회원가입이 완료되었어요. 로그인해 주세요.');
            } catch (err) {
              // 서버가 막은 이유에 맞춰 다시 확인해야 할 단계를 되돌림
              if (err.code === 'DUPLICATE_LOGIN_ID') setCheckedUsername('');
              if (err.code === 'DUPLICATE_EMAIL') setCheckedEmail('');
              if (err.code === 'EMAIL_NOT_VERIFIED') setProof('');
              setError(err.message);
            } finally {
              submitting.current = false;
              setBusy(false);
            }
            return;
          }
          // 서버 로그인 (F-01-02) · 로그인 기록(ACCESS_LOG)은 서버가 남겨요
          setBusy(true);
          try {
            const u = await accountService.login(email, password);
            onLogin(u);
          } catch (err) {
            setError(err.message);
          } finally {
            setBusy(false);
          }
        }}
      >
        {signup && (
          <div className="field">
            <label htmlFor="signup-id">아이디 *</label>
            <div className="input-action">
              <input
                id="signup-id"
                value={username}
                onChange={(e) => {
                  setUsername(e.target.value);
                  setCheckedUsername('');
                }}
                placeholder="영문·숫자·밑줄 4~50자"
                autoComplete="username"
              />
              <button className="btn secondary" type="button" onClick={() => check('username')}>
                중복확인
              </button>
            </div>
            {checkedUsername === username && username && (
              <small role="status">사용 가능한 아이디입니다.</small>
            )}
          </div>
        )}
        {signup && (
          <label className="field">
            <span>비밀번호 *</span>
            <input
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              type="password"
              minLength={8}
              required
              autoComplete={signup ? 'new-password' : 'current-password'}
              placeholder="8자 이상"
            />
          </label>
        )}
        {signup && (
          <label className="field">
            <span>비밀번호 확인 *</span>
            <input
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              type="password"
              required
              autoComplete="new-password"
            />
            {confirm && (
              <small role="status">
                {password === confirm ? '비밀번호가 일치합니다.' : '비밀번호가 일치하지 않습니다.'}
              </small>
            )}
          </label>
        )}
        <div className="field">
          <label htmlFor="account-email">{signup ? '이메일 *' : '아이디 *'}</label>
          <div className="input-action">
            <input
              id="account-email"
              name="email"
              type={signup ? 'email' : 'text'}
              required
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                setCheckedEmail('');
                setProof('');
                setPendingId('');
                setDevCode('');
                setExpiresAt(0);
                setVerificationMessage('');
              }}
              autoComplete={signup ? 'off' : 'username'}
            />
            {signup && (
              <button className="btn secondary" type="button" onClick={() => check('email')}>
                중복확인
              </button>
            )}
          </div>
          {signup && checkedEmail === email && email && (
            <small role="status">사용 가능한 이메일입니다.</small>
          )}
        </div>{' '}
        {!signup && (
          <label className="field">
            <span>비밀번호 *</span>
            <input
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              type="password"
              minLength={8}
              required
              autoComplete={signup ? 'new-password' : 'current-password'}
            />
          </label>
        )}
        {signup && (
          <>
            {/* 이메일 인증 UI: 실제 메일 발송 전이라 숨김 (코드는 그대로 · 켜려면 member-api.js EMAIL_VERIFICATION_REQUIRED = true) */}
            {EMAIL_VERIFICATION_REQUIRED && (
              <div className="email-verification">
                <h3>이메일 인증</h3>
                <p>
                  인증번호는 5분 동안 유효하며 5번 틀리면 다시 받아야 해요. 메일 발송 기능은 준비
                  중이라 개발 환경에서는 인증번호가 아래에 표시됩니다.
                </p>
                <button
                  className="btn secondary"
                  type="button"
                  onClick={async () => {
                    // 서버 인증번호 발급: verificationId(번호표)를 받아 둔다
                    try {
                      if (checkedEmail !== email || !email)
                        throw Error('이메일 중복확인을 먼저 해주세요.');
                      const result = await memberApi.sendEmailCode(email);
                      setPendingId(result.verificationId);
                      setDevCode(result.devCode || '');
                      setExpiresAt(Date.parse(result.expiresAt));
                      setNow(Date.now());
                      setProof('');
                      setCode('');
                      setVerificationMessage(
                        result.devCode
                          ? '개발 환경: 메일 대신 아래 인증번호를 사용하세요.'
                          : '인증번호를 발급했어요. (메일 발송 기능은 아직 연결되지 않았어요)',
                      );
                    } catch (err) {
                      if (err.code === 'DUPLICATE_EMAIL') setCheckedEmail('');
                      setVerificationMessage(err.message);
                    }
                  }}
                >
                  {pendingId ? '인증번호 다시 받기' : '인증번호 발송'}
                </button>
                {devCode && !proof && (
                  <p>
                    개발용 인증번호: <b>{devCode}</b> ·{' '}
                    {expired
                      ? '만료됨'
                      : `${Math.max(0, Math.ceil((expiresAt - now) / 1000))}초 남음`}
                  </p>
                )}
                {pendingId && !devCode && !proof && (
                  <p>
                    {expired
                      ? '인증번호 만료됨'
                      : `인증번호 유효 시간 ${Math.max(0, Math.ceil((expiresAt - now) / 1000))}초 남음`}
                  </p>
                )}
                <div className="input-action">
                  <input
                    aria-label="인증번호"
                    inputMode="numeric"
                    maxLength={6}
                    value={code}
                    onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                    placeholder="6자리 인증번호"
                  />
                  <button
                    className="btn secondary"
                    type="button"
                    disabled={!!proof}
                    onClick={async () => {
                      // 서버 인증번호 확인: 성공하면 번호표를 가입 요청에 사용
                      try {
                        if (!pendingId) throw Error('인증번호를 먼저 요청해 주세요');
                        if (!/^\d{6}$/.test(code)) throw Error('6자리 인증번호를 입력해 주세요');
                        await memberApi.confirmEmailCode(pendingId, code);
                        setProof(pendingId);
                        setVerificationMessage('이메일 인증이 완료됐어요');
                      } catch (err) {
                        setProof('');
                        // 만료 · 5회 실패 → 번호표가 폐기되어 다시 발송해야 함
                        if (['VERIFICATION_EXPIRED', 'VERIFICATION_LIMIT'].includes(err.code)) {
                          setPendingId('');
                          setDevCode('');
                          setExpiresAt(0);
                        }
                        setVerificationMessage(err.message);
                      }
                    }}
                  >
                    인증확인
                  </button>
                </div>
                <p role="status">
                  {proof
                    ? '✓ 이메일 인증 완료'
                    : expired
                      ? '인증 코드가 맞지 않거나 만료됐어요. 인증번호를 다시 받아 주세요.'
                      : verificationMessage}
                </p>
              </div>
            )}
            <label className="field">
              <span>생년월일 * · 만 14세 이상</span>
              <input type="date" name="birth" required />
              <small>주민등록번호 전체는 수집하지 않습니다.</small>
            </label>
            <fieldset className="gender-choice">
              <legend>성별 *</legend>
              {['남성', '여성'].map((g) => (
                <button
                  key={g}
                  type="button"
                  aria-pressed={gender === g}
                  className={'btn ' + (gender === g ? '' : 'secondary')}
                  onClick={() => setGender(g)}
                >
                  {g}
                </button>
              ))}
            </fieldset>
            <div className="agreement-list">
              <label className="checkline">
                <input
                  type="checkbox"
                  checked={Object.values(consent).every(Boolean)}
                  onChange={(e) =>
                    setConsent({
                      terms: e.target.checked,
                      privacy: e.target.checked,
                      financial: e.target.checked,
                    })
                  }
                />
                전체 동의
              </label>
              {agreements.map(([key, label, text]) => (
                <div key={key}>
                  <label className="checkline">
                    <input
                      type="checkbox"
                      checked={consent[key]}
                      onChange={(e) => setConsent({ ...consent, [key]: e.target.checked })}
                    />
                    {label}
                  </label>
                  <details>
                    <summary>동의문 초안 보기 · 내용·버전 확인 필요</summary>
                    <p>{text}</p>
                  </details>
                </div>
              ))}
            </div>
          </>
        )}
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <button disabled={busy} className="btn" type="submit">
          {busy ? '처리 중…' : signup ? '동의하고 회원가입' : '로그인'}
        </button>
      </form>
      {/* 비밀번호 재설정: 서버 API가 아직 없어 화면에서만 숨김 (나중에 SHOW_PASSWORD_RESET = true) */}
      {SHOW_PASSWORD_RESET && !signup && (
        <button className="text-btn" onClick={() => go('reset')}>
          비밀번호를 잊으셨나요?
        </button>
      )}
      <p className="auth-switch">
        {signup ? '이미 계정이 있으신가요?' : '아직 회원이 아니신가요?'}{' '}
        <button
          onClick={() => {
            setError('');
            setSuccess('');
            go(signup ? 'login' : 'signup');
          }}
        >
          {signup ? '로그인' : '일반 회원가입'}
        </button>
      </p>
    </section>
  );
}
