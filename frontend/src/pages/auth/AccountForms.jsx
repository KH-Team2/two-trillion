// 가입·로그인·이메일 인증·아이디 찾기·비밀번호 재설정·빠른 체험 UI입니다.
// 회원가입(F-01-01) · 로그인(F-01-02) · 아이디 찾기 · 비밀번호 재설정(F-01-03)은 Spring Boot API에 연결되어 있습니다 (memberApi · accountService.login).
// 빠른 체험은 서버 없이 이 브라우저에서만 동작합니다. 관리자 권한은 DB(MEMBER.ROLE = ADMIN)에서 지정합니다.
import React, { useState, useEffect, useRef } from 'react';
import { accountService } from '@/api/account-service.js';
import {
  EMAIL_VERIFICATION_REQUIRED,
  memberApi,
  passwordResetFormError,
  signupFormError,
} from '@/api/member-api.js';
import { recordAccess } from '@/api/prototype-records.js';
import { consentVersions } from '@/config/consent-versions.js';
// 동의서 버전은 별도 설정합니다. 미확정 버전의 서버 DTO는 생성할 수 없습니다.
const notice =
  '브라우저 내 기능 시연입니다. 계정·권한·잠금 정보는 이 브라우저에만 저장되며 서버 보안 기능은 아직 연결되지 않았습니다. 동의서 운영 버전 미확정 시 로컬 체험만 제공하며 서버에 동의를 저장하지 않습니다. 실제 사용하는 비밀번호·개인정보는 입력하지 마세요.';
const agreements = [
  [
    'terms',
    '[필수] 서비스 이용약관 동의',
    '희망환급은 소득·지출 입력, 예상 결과 조회, 시뮬레이션 및 안내를 제공합니다. 만 14세 이상 가입할 수 있으며 관리자는 기존 계정에 권한을 부여합니다. 본인에게 사용 권한이 있는 자료만 등록해야 합니다. 계산·분류·AI는 시연이며 실제 신고·자격 확정·세무 자문이 아닙니다. 마이페이지에서 탈퇴할 수 있으며 마지막 관리자는 인계가 필요합니다. 운영 주체·시행일·분쟁 처리 등은 서비스 정책 확정 전 보완해야 합니다.',
    'TERMS_VERSION',
  ],
  [
    'privacy',
    '[필수] 개인정보 수집·이용 동의',
    '목적: 계정 식별, 로그인, 연령 확인, 그룹 비교 및 서비스 지원. 항목: 아이디, 이메일, 비밀번호 파생 해시, 생년월일, 성별, 동의 이력. 보유기간·보존 정책은 운영 검토가 필요합니다. 탈퇴 후 30일 유예기간 동안 아이디·이메일을 유지하고 이후 식별 정보 처리 예정입니다. 회원가입 정보는 희망환급 서버(DB)에 저장합니다. 주민등록번호 전체는 수집하지 않습니다. 필수 동의 거부 시 가입할 수 없습니다. 운영 주체·문의처·법적 보존 대상 및 기간·위탁 정책은 확정 전입니다.',
    'PRIVACY_VERSION',
  ],
  [
    'financial',
    '[필수] 금융 정보 저장 동의',
    '목적: 연말정산 예상 계산, 거래 자동 분류, 결과 조회 및 시뮬레이션. 저장 항목: 사용자가 제출한 급여·기납부세액·결제수단별 지출액, 거래일·상호명·금액·결제수단, 파일 처리 메타정보, 가족 수·월세·주거 및 감면 자격 입력, 계산·시뮬레이션 기록. 원본 CSV·XLSX 파일 자체와 카드번호 전체·계좌 비밀번호는 저장하지 않습니다. 탈퇴 요청 시 즉시 삭제하지 않으며 30일 유예 및 이후 식별 정보 처리 정책은 운영 검토가 필요합니다. 업로드 파일별 삭제도 가능합니다. 거부 시 가입할 수 없습니다. 실제 금융기관·국세청 조회나 운영 서버 저장은 연결되지 않았습니다.',
    'FINANCE_VERSION',
  ],
];
// 남은 시간(초) · 0 아래로 내려가지 않음
const secondsLeft = (expiresAt, now) => Math.max(0, Math.ceil((expiresAt - now) / 1000));

// 아이디 찾기 · 비밀번호 재설정 (F-01-03) · 서버 API에 연결됨 (memberApi)
// 아이디 찾기: 이메일 → 일부를 가린 아이디 · 재설정: 이메일 → 인증 코드 메일(10분) → 코드 + 새 비밀번호
function AccountRecovery({ go }) {
  const [tab, setTab] = useState('id'),
    [email, setEmail] = useState(''),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(''),
    [maskedId, setMaskedId] = useState('');
  const [sent, setSent] = useState(false),
    [expiresAt, setExpiresAt] = useState(0),
    [now, setNow] = useState(Date.now()),
    [devCode, setDevCode] = useState(''),
    [sendMessage, setSendMessage] = useState(''),
    [code, setCode] = useState(''),
    [newPassword, setNewPassword] = useState(''),
    [newPasswordConfirm, setNewPasswordConfirm] = useState(''),
    [done, setDone] = useState(false);
  const submitting = useRef(false); // 요청 중복 전송 방지
  useEffect(() => {
    if (!expiresAt) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [expiresAt]);
  const expired = expiresAt > 0 && now >= expiresAt;
  // 코드를 다시 받아야 하는 상태로 되돌림 (이메일 변경 · 만료 · 5번 틀림)
  const resetCode = () => {
    setSent(false);
    setExpiresAt(0);
    setDevCode('');
    setCode('');
  };
  const run = async (task) => {
    if (submitting.current) return;
    submitting.current = true;
    setBusy(true);
    setError('');
    try {
      await task();
    } catch (err) {
      setError(err.message);
    } finally {
      submitting.current = false;
      setBusy(false);
    }
  };
  const findId = () =>
    run(async () => {
      setMaskedId('');
      if (!email.trim()) throw Error('이메일을 입력해 주세요');
      const result = await memberApi.findLoginId(email);
      setMaskedId(result.maskedLoginId);
    });
  const sendCode = () =>
    run(async () => {
      if (!email.trim()) throw Error('이메일을 입력해 주세요');
      try {
        const result = await memberApi.sendPasswordResetCode(email);
        setSent(true);
        setExpiresAt(Date.parse(result.expiresAt));
        setNow(Date.now());
        setDevCode(result.devCode || '');
        setCode('');
        setSendMessage(
          result.devCode
            ? '개발 환경: 메일 대신 아래 인증 코드를 사용하세요.'
            : '인증 코드를 메일로 보냈어요. 메일이 보이지 않으면 스팸함도 확인해 주세요.',
        );
      } catch (err) {
        resetCode();
        throw err;
      }
    });
  const complete = () =>
    run(async () => {
      if (expired) {
        resetCode();
        throw Error('인증 코드를 다시 받아 주세요');
      }
      const form = { email, code, newPassword, newPasswordConfirm };
      const message = passwordResetFormError(form);
      if (message) throw Error(message);
      try {
        await memberApi.completePasswordReset(form);
      } catch (err) {
        // 만료 · 5번 틀림 · 받은 코드 없음 → 코드를 다시 받아야 함
        if (['RESET_CODE_EXPIRED', 'RESET_CODE_LIMIT'].includes(err.code)) resetCode();
        throw err;
      }
      setNewPassword('');
      setNewPasswordConfirm('');
      resetCode();
      setDone(true);
    });
  const switchTab = (next) => {
    setTab(next);
    setError('');
    setMaskedId('');
  };
  return (
    <section className="card auth-card">
      <h2>아이디 찾기 · 비밀번호 재설정</h2>
      <p className="muted">
        가입할 때 입력한 이메일로 아이디를 확인하거나 비밀번호를 새로 정해요. 비밀번호는 암호화해
        저장하기 때문에 원래 비밀번호는 알려 드릴 수 없어요.
      </p>
      <div className="tabs" role="tablist">
        {[
          ['id', '아이디 찾기'],
          ['password', '비밀번호 재설정'],
        ].map(([key, label]) => (
          <button
            key={key}
            type="button"
            role="tab"
            aria-selected={tab === key}
            className={tab === key ? 'active' : ''}
            onClick={() => switchTab(key)}
          >
            {label}
          </button>
        ))}
      </div>
      {done ? (
        <>
          <p className="notice" role="status">
            비밀번호를 바꿨어요. 새 비밀번호로 로그인해 주세요.
          </p>
          <button className="btn" type="button" onClick={() => go('login')}>
            로그인하러 가기
          </button>
        </>
      ) : (
        <form
          noValidate
          onSubmit={(e) => {
            e.preventDefault();
            if (tab === 'id') findId();
            else if (sent) complete();
            else sendCode();
          }}
        >
          <div className="field">
            <label htmlFor="recovery-email">이메일 *</label>
            <div className="input-action">
              <input
                id="recovery-email"
                type="email"
                required
                value={email}
                onChange={(e) => {
                  setEmail(e.target.value);
                  setMaskedId('');
                  resetCode();
                }}
                autoComplete="email"
              />
              {tab === 'password' && sent && (
                <button className="btn secondary" type="button" disabled={busy} onClick={sendCode}>
                  다시 받기
                </button>
              )}
            </div>
          </div>
          {tab === 'id' && maskedId && (
            <p className="notice" role="status">
              가입한 아이디는 <b>{maskedId}</b> 예요.
            </p>
          )}
          {tab === 'password' && sent && (
            <>
              <p role="status">{sendMessage}</p>
              {devCode && (
                <p>
                  개발용 인증 코드: <b>{devCode}</b>
                </p>
              )}
              <p>
                {expired
                  ? '인증 코드가 만료됐어요. 다시 받아 주세요.'
                  : `인증 코드 유효 시간 ${secondsLeft(expiresAt, now)}초 남음 · 5번 틀리면 다시 받아야 해요`}
              </p>
              <label className="field">
                <span>인증 코드 *</span>
                <input
                  inputMode="numeric"
                  maxLength={6}
                  value={code}
                  onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                  placeholder="6자리 인증 코드"
                  autoComplete="one-time-code"
                />
              </label>
              <label className="field">
                <span>새 비밀번호 *</span>
                <input
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  placeholder="8자 이상"
                  autoComplete="new-password"
                />
              </label>
              <label className="field">
                <span>새 비밀번호 확인 *</span>
                <input
                  type="password"
                  value={newPasswordConfirm}
                  onChange={(e) => setNewPasswordConfirm(e.target.value)}
                  autoComplete="new-password"
                />
                {newPasswordConfirm && (
                  <small role="status">
                    {newPassword === newPasswordConfirm
                      ? '비밀번호가 일치합니다.'
                      : '비밀번호가 일치하지 않습니다.'}
                  </small>
                )}
              </label>
            </>
          )}
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <button disabled={busy} className="btn" type="submit">
            {busy
              ? '처리 중…'
              : tab === 'id'
                ? '아이디 찾기'
                : sent
                  ? '비밀번호 바꾸기'
                  : '인증 코드 받기'}
          </button>
        </form>
      )}
      <p className="auth-switch">
        비밀번호가 기억나셨나요?{' '}
        <button type="button" onClick={() => go('login')}>
          로그인
        </button>
      </p>
    </section>
  );
}

export function AccountForms({ page, go, onLogin }) {
  // 아이디 찾기 · 비밀번호 재설정은 서버에 연결된 별도 카드 (F-01-03)
  if (page === 'reset') return <AccountRecovery go={go} />;
  return <AccountSignupLogin page={page} go={go} onLogin={onLogin} />;
}

function AccountSignupLogin({ page, go, onLogin }) {
  const [error, setError] = useState(''),
    [busy, setBusy] = useState(false),
    [setup, setSetup] = useState(false);
  const [email, setEmail] = useState(''),
    [username, setUsername] = useState(''),
    [checkedEmail, setCheckedEmail] = useState(''),
    [checkedUsername, setCheckedUsername] = useState(''),
    [gender, setGender] = useState('');
  const [password, setPassword] = useState(''),
    [confirm, setConfirm] = useState('');
  const [code, setCode] = useState(''),
    [demoCode, setDemoCode] = useState(''),
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
  const signup = page === 'signup' || setup;
  const serverSignup = page === 'signup' && !setup; // 서버 API로 가입하는 일반 회원가입
  const serverLogin = page === 'login' && !setup; // 서버 API로 로그인 (F-01-02)
  useEffect(() => {
    if (!expiresAt) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [expiresAt]);
  const expired = expiresAt > 0 && now >= expiresAt;
  const check = async (kind) => {
    if (serverSignup) {
      // 서버 중복확인: 사용 불가면 서버 문구를 표시
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
      return;
    }
    try {
      const available =
        kind === 'username'
          ? accountService.checkUsername(username)
          : accountService.checkEmail(email);
      if (!available)
        throw Error(`이미 사용 중인 ${kind === 'username' ? '아이디' : '이메일'}입니다.`);
      if (kind === 'username') setCheckedUsername(username);
      else setCheckedEmail(email);
      setError('');
    } catch (err) {
      setError(err.message);
    }
  };
  return (
    <section className="card auth-card" key={page}>
      {/* [수정 11/17] 서버에 연결된 가입 · 로그인 화면에는 표시 없음 · 빠른 체험 화면만 표시 */}
      {!(serverSignup || serverLogin) && (
        <span className="tag amber">브라우저 시연 · 서버 미연동</span>
      )}
      <h2>
        {setup ? '최초 관리자 초기 설정' : signup ? '희망환급 회원가입' : '다시 만나 반가워요'}
      </h2>
      <p className="muted">
        {serverSignup
          ? '입력한 회원 정보는 희망환급 서버에 저장됩니다. 비밀번호는 암호화하여 저장하며, 실명은 받지 않습니다.'
          : serverLogin
            ? '희망환급에 가입한 아이디로 로그인해요. 비밀번호를 5번 틀리면 10분 동안 로그인할 수 없어요.'
            : notice}
      </p>
      {success && (
        <p className="notice" role="status">
          {success}
        </p>
      )}
      {setup && (
        <p className="notice amber">
          이 브라우저에 관리자 계정이 없을 때 최초 1회 설정합니다. 운영 서버의 비공개 초기 설정은
          별도 구현이 필요합니다.
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
          if (serverSignup) {
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
              setDemoCode('');
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
          setBusy(true);
          try {
            if (signup) {
              if (checkedUsername !== username || !username)
                throw Error('아이디 중복확인을 해주세요.');
              if (checkedEmail !== email || !email) throw Error('이메일 중복확인을 해주세요.');
              if (!proof || expired)
                throw Error('이메일 인증을 완료해주세요. 만료된 경우 다시 인증해주세요.');
              const payload = {
                username,
                gender,
                verificationToken: proof,
                email,
                birth: String(f.get('birth') || ''),
                password,
                confirm,
                ...consent,
              };
              await (setup ? accountService.bootstrap(payload) : accountService.signUp(payload));
              setSetup(false);
              setConsent({ terms: false, privacy: false, financial: false });
              setProof('');
              setPassword('');
              setConfirm('');
              go('login');
              setError('계정이 이 브라우저에 등록되었습니다. 아이디 또는 이메일로 로그인해주세요.');
            } else {
              const u = await accountService.login(email, password);
              recordAccess('LOGIN');
              onLogin(u);
            }
          } catch (err) {
            if (!signup) recordAccess('LOGIN', 'FAIL');
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
              placeholder={serverSignup ? '8자 이상' : '시연용 비밀번호 8자 이상'}
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
                setDemoCode('');
                setExpiresAt(0);
                setVerificationMessage('');
              }}
              autoComplete={serverLogin ? 'username' : 'off'}
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
            {/* 이메일 인증 UI: frontend/.env 의 VITE_EMAIL_VERIFICATION_REQUIRED=true 일 때만 보임 (member-api.js) */}
            {EMAIL_VERIFICATION_REQUIRED && (
              <div className="email-verification">
                <h3>{serverSignup ? '이메일 인증' : '이메일 인증 · 체험'}</h3>
                <p>
                  {serverSignup
                    ? '입력한 이메일로 인증번호를 보내 드려요. 인증번호는 5분 동안 유효하며 5번 틀리면 다시 받아야 해요.'
                    : '이메일 발송 API가 연결되지 않아 실제 메일은 보내지 않습니다. 아래에 표시되는 체험 인증번호로 동작을 확인하세요.'}
                </p>
                <button
                  className="btn secondary"
                  type="button"
                  onClick={async () => {
                    if (serverSignup) {
                      // 서버 인증번호 발급: verificationId(번호표)를 받아 둔다
                      try {
                        if (checkedEmail !== email || !email)
                          throw Error('이메일 중복확인을 먼저 해주세요.');
                        const result = await memberApi.sendEmailCode(email);
                        setPendingId(result.verificationId);
                        setDemoCode(result.devCode || '');
                        setExpiresAt(Date.parse(result.expiresAt));
                        setNow(Date.now());
                        setProof('');
                        setCode('');
                        setVerificationMessage(
                          result.devCode
                            ? '개발 환경: 메일 대신 아래 인증번호를 사용하세요.'
                            : '인증번호를 메일로 보냈어요. 메일이 보이지 않으면 스팸함도 확인해 주세요.',
                        );
                      } catch (err) {
                        if (err.code === 'DUPLICATE_EMAIL') setCheckedEmail('');
                        setVerificationMessage(err.message);
                      }
                      return;
                    }
                    try {
                      if (checkedEmail !== email || !email)
                        throw Error('이메일 중복확인을 먼저 해주세요.');
                      const result = accountService.sendEmailCode(email);
                      setDemoCode(result.demoCode);
                      setExpiresAt(result.expiresAt);
                      setNow(Date.now());
                      setProof('');
                      setCode('');
                      setVerificationMessage(
                        '체험 인증번호를 생성했습니다. 실제 이메일은 발송되지 않았습니다.',
                      );
                    } catch (err) {
                      setVerificationMessage(err.message);
                    }
                  }}
                >
                  {serverSignup
                    ? pendingId
                      ? '인증번호 다시 받기'
                      : '인증번호 발송'
                    : '인증번호 발송 (체험)'}
                </button>
                {demoCode && !(serverSignup && proof) && (
                  <p>
                    {serverSignup ? '개발용 인증번호' : '체험 인증번호'}: <b>{demoCode}</b> ·{' '}
                    {expired
                      ? '만료됨'
                      : `${Math.max(0, Math.ceil((expiresAt - now) / 1000))}초 남음`}
                  </p>
                )}
                {serverSignup && pendingId && !demoCode && !proof && (
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
                    disabled={serverSignup && !!proof}
                    onClick={async () => {
                      if (serverSignup) {
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
                            setDemoCode('');
                            setExpiresAt(0);
                          }
                          setVerificationMessage(err.message);
                        }
                        return;
                      }
                      try {
                        setProof(accountService.verifyEmailCode(email, code));
                        setVerificationMessage('인증 성공 (브라우저 체험)');
                      } catch (err) {
                        setProof('');
                        setVerificationMessage(err.message);
                      }
                    }}
                  >
                    인증확인
                  </button>
                </div>
                <p role="status">
                  {serverSignup && proof
                    ? '✓ 이메일 인증 완료'
                    : expired
                      ? serverSignup
                        ? '인증 코드가 맞지 않거나 만료됐어요. 인증번호를 다시 받아 주세요.'
                        : '인증번호가 만료되었습니다. 다시 발송해주세요.'
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
              {/* [수정 11/17] 서버 회원가입 화면에서는 「검토용 초안」 안내 문구 삭제 */}
              {!serverSignup && (
                <p>
                  아래 내용은 검토용 초안입니다. 이용약관·개인정보·금융 동의 버전은 각각 미확정이며,
                  시연 동의일시만 따로 보관합니다. 운영 전 전문과 버전 확정이 필요합니다.
                </p>
              )}
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
              {agreements.map(([key, label, text, versionKey]) => (
                <div key={key}>
                  <label className="checkline">
                    <input
                      type="checkbox"
                      checked={consent[key]}
                      onChange={(e) => setConsent({ ...consent, [key]: e.target.checked })}
                    />
                    {label}
                    {/* 서버 회원가입: 동의서 버전 표시 (VITE_*_VERSION · 가입 요청에도 같은 값을 보냄) */}
                    {serverSignup &&
                      consentVersions[versionKey] &&
                      ` (v${consentVersions[versionKey]})`}
                  </label>
                  <details>
                    <summary>
                      {serverSignup ? '동의문 보기' : '동의문 초안 보기 · 내용·버전 확인 필요'}
                    </summary>
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
          {busy
            ? '처리 중…'
            : setup
              ? '최초 관리자 만들기 (시연)'
              : signup
                ? '동의하고 회원가입'
                : '로그인'}
        </button>
      </form>
      {/* 아이디 찾기 · 비밀번호 재설정 (F-01-03) · 서버 로그인 화면에서만 */}
      {serverLogin && (
        <button className="text-btn" type="button" onClick={() => go('reset')}>
          아이디 · 비밀번호를 잊으셨나요?
        </button>
      )}
      <p className="auth-switch">
        {signup ? '이미 계정이 있으신가요?' : '아직 회원이 아니신가요?'}{' '}
        <button
          onClick={() => {
            setSetup(false);
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
export function MemberManagement({ onChange }) {
  const [name, setName] = useState(''),
    [rev, setRev] = useState(0),
    [message, setMessage] = useState('');
  let users = [];
  try {
    users = accountService.members(name);
  } catch {
    return <p role="alert">관리자 권한이 필요합니다.</p>;
  }
  return (
    <>
      <h3>회원 관리</h3>
      <p className="muted">
        {accountService.isDemo()
          ? '빠른 체험용 회원 목록입니다. 권한 변경은 체험 데이터에만 반영됩니다.'
          : '이 브라우저에서 가입한 계정의 권한만 변경하는 시연입니다.'}
      </p>
      <label className="field">
        <span>아이디·이메일·계정 상태 검색 · 부분 일치</span>
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="아이디, 이메일 또는 ACTIVE / WITHDRAWN"
        />
      </label>
      <p>{users.length}명</p>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>아이디</th>
              <th>이메일</th>
              <th>가입일</th>
              <th>계정 상태</th>
              <th>현재 권한</th>
              <th>권한 관리</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td>{u.username || u.name}</td>
                <td>{u.email.replace(/^(.{1,2}).*(@.*)$/, '$1***$2')}</td>
                <td>{new Date(u.joinedAt).toLocaleDateString('ko-KR')}</td>
                <td>{u.status || 'ACTIVE'}</td>
                <td>
                  <span className={'tag ' + (u.role === 'ADMIN' ? 'blue' : 'gray')}>{u.role}</span>
                </td>
                <td>
                  <button
                    className="text-btn"
                    onClick={() => {
                      if (
                        !window.confirm(
                          `${u.name} (${u.email}) 회원의 관리자 권한을 ${u.role === 'ADMIN' ? '해제' : '부여'}하시겠습니까? (브라우저 시연)`,
                        )
                      )
                        return;
                      try {
                        accountService.setRole(u.id, u.role === 'ADMIN' ? 'USER' : 'ADMIN');
                        recordAccess('MEMBER_ROLE_CHANGE');
                        setMessage('권한을 변경했습니다.');
                        setRev(rev + 1);
                        onChange();
                      } catch (err) {
                        recordAccess('MEMBER_ROLE_CHANGE', 'FAIL');
                        setMessage(err.message);
                      }
                    }}
                  >
                    {u.role === 'ADMIN' ? '관리자 권한 해제' : '관리자 권한 부여'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {!users.length && <p>검색 결과가 없습니다.</p>}
      {message && <p role="status">{message}</p>}
    </>
  );
}
// 회원 탈퇴(F-01-05) · 서버 로그인 회원은 back API · 체험 계정은 브라우저 저장
export function WithdrawAccount({ onDone }) {
  const [open, setOpen] = useState(false),
    [error, setError] = useState(''),
    [busy, setBusy] = useState(false);
  const server = Boolean(accountService.current()?.server);
  return (
    <>
      <h3>회원 탈퇴</h3>
      {server ? (
        <p className="muted">
          탈퇴하면 바로 로그아웃되고 이 계정으로 다시 로그인할 수 없어요. 30일 유예기간 동안은
          아이디·이메일을 그대로 보관하므로 같은 아이디·이메일로 다시 가입할 수 없어요. 30일이
          지나면 아이디·이메일 등 회원을 알아볼 수 있는 정보를 식별할 수 없게 처리해요. 문의·접속
          기록은 보존돼요.
        </p>
      ) : (
        <p className="muted">
          브라우저 체험 계정 탈퇴예요. 계정 상태가 WITHDRAWN으로 바뀌고 로그아웃돼요. 30일 뒤 식별
          정보 처리는 브라우저 체험에서는 하지 않아요.
        </p>
      )}
      <button className="btn secondary danger" onClick={() => setOpen(!open)}>
        회원 탈퇴
      </button>
      {open && (
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            setError('');
            const f = new FormData(e.currentTarget);
            if (f.get('confirm') !== 'on') {
              setError('탈퇴 및 30일 유예 안내를 확인해주세요.');
              return;
            }
            if (!window.confirm('정말 탈퇴하시겠어요? 탈퇴하면 바로 로그아웃돼요.')) return;
            setBusy(true);
            try {
              await accountService.withdraw(String(f.get('password') || ''));
              onDone();
            } catch (err) {
              setError(err.message);
            } finally {
              setBusy(false);
            }
          }}
        >
          {!server && <p>마지막 관리자는 다른 관리자에게 권한을 부여한 후 탈퇴할 수 있습니다.</p>}
          <label className="field">
            <span>현재 비밀번호 재확인</span>
            <input type="password" name="password" autoComplete="current-password" required />
          </label>
          <label className="checkline">
            <input type="checkbox" name="confirm" required />
            로그인 차단·30일 유예·이후 식별 정보 처리 안내를 확인했어요.
          </label>
          <button className="btn danger" disabled={busy}>
            {busy ? '탈퇴 처리 중…' : '재확인하고 탈퇴하기'}
          </button>
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
        </form>
      )}
    </>
  );
}
