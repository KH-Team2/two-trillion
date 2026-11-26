// 회원정보 조회·수정 폼입니다.
// - 서버 로그인 회원: back API(F-01-04)로 조회 · 저장 (ServerProfileDetails · ServerPasswordChange)
// [수정 11/25] 빠른 체험 계정용 브라우저 폼(ProfileDetails)과 accountService import 삭제 · 이유: 체험판 기능 삭제
import React, { useEffect, useState } from 'react';
import {
  COMPANY_SIZE_OPTIONS,
  memberApi,
  passwordChangeFormError,
  profileFormError,
  todayDate,
} from '@/api/member-api.js';
import { Btn, Field } from '@/components/common/site-primitives.jsx';

// 서버 응답 → 폼 값 (빈 값은 '' · select · input에 null을 넣지 않음)
function toForm(profile) {
  return {
    gender: profile.gender || '',
    militaryMonths: String(profile.militaryMonths ?? 0),
    hireDate: profile.hireDate || '',
    companySize: profile.companySize || '',
    homelessHeadYn: profile.homelessHeadYn || '',
  };
}

// 서버 로그인 회원의 회원정보 · 자격 정보 조회 · 수정 (F-01-04)
// 아이디 · 이메일 · 생년월일은 보기만 · 권한 · 계정 상태는 화면에 없음
export function ServerProfileDetails({ onSaved }) {
  const [profile, setProfile] = useState(null),
    [form, setForm] = useState(null),
    [message, setMessage] = useState(''),
    [loadError, setLoadError] = useState(''),
    [saving, setSaving] = useState(false);

  useEffect(() => {
    let active = true; // 화면을 떠난 뒤 늦게 온 응답은 무시
    memberApi
      .getMyProfile()
      .then((result) => {
        if (!active) return;
        setProfile(result);
        setForm(toForm(result));
      })
      .catch((err) => active && setLoadError(err.message));
    return () => {
      active = false;
    };
  }, []);

  if (loadError) return <p role="alert">{loadError}</p>;
  if (!form) return <p role="status">회원정보를 불러오는 중이에요.</p>;

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  return (
    <form
      onSubmit={async (e) => {
        e.preventDefault();
        const error = profileFormError(form);
        if (error) {
          setMessage(error);
          return;
        }
        setSaving(true); // 연달아 눌러도 요청은 한 번만
        try {
          const saved = await memberApi.updateMyProfile(form);
          setProfile(saved);
          setForm(toForm(saved));
          setMessage('저장했어요. 다음 계산부터 반영돼요.');
          onSaved?.(saved);
        } catch (err) {
          setMessage(err.message);
        } finally {
          setSaving(false);
        }
      }}
    >
      <h3>회원정보 조회·수정</h3>
      <p>
        아이디·이메일·생년월일은 바꿀 수 없어요. 군 복무·입사일·회사 규모·무주택 세대주 정보는 예상
        세액 계산과 청년 혜택 진단에 쓰여요.
      </p>
      <div className="form-grid">
        <Field label="아이디">
          <input readOnly value={profile.loginId || ''} />
        </Field>
        <Field label="이메일">
          <input readOnly value={profile.maskedEmail || ''} />
        </Field>
        <Field label="생년월일">
          <input readOnly value={profile.birthDate || ''} />
        </Field>
        <Field label="성별">
          <select required value={form.gender} onChange={set('gender')}>
            <option value="">선택해 주세요</option>
            <option value="M">남성</option>
            <option value="F">여성</option>
          </select>
        </Field>
        <Field label="군 복무 개월 수" hint="복무 이력이 없으면 0개월을 입력해 주세요.">
          <input
            required
            type="number"
            min="0"
            step="1"
            value={form.militaryMonths}
            onChange={set('militaryMonths')}
          />
        </Field>
        <Field label="입사일">
          <input type="date" max={todayDate()} value={form.hireDate} onChange={set('hireDate')} />
        </Field>
        <Field label="회사 규모">
          <select value={form.companySize} onChange={set('companySize')}>
            <option value="">선택 안 함</option>
            {COMPANY_SIZE_OPTIONS.map(([code, label]) => (
              <option key={code} value={code}>
                {label}
              </option>
            ))}
          </select>
        </Field>
        <Field label="무주택 세대주 여부" hint="무주택이면서 세대주인 경우 예를 선택해 주세요.">
          <select required value={form.homelessHeadYn} onChange={set('homelessHeadYn')}>
            <option value="">선택해 주세요</option>
            <option value="Y">예</option>
            <option value="N">아니요</option>
          </select>
        </Field>
      </div>
      <Btn type="submit" disabled={saving}>
        {saving ? '저장 중…' : '회원정보 저장'}
      </Btn>
      <p role="status">{message}</p>
    </form>
  );
}

const EMPTY_PASSWORD_FORM = { currentPassword: '', newPassword: '', newPasswordConfirm: '' };

// 서버 로그인 회원의 비밀번호 변경 (F-01-04 · 현재 비밀번호 확인 · 잊었으면 로그인 화면의 재설정)
export function ServerPasswordChange({ notify }) {
  const [form, setForm] = useState(EMPTY_PASSWORD_FORM),
    [message, setMessage] = useState(''),
    [saving, setSaving] = useState(false);
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  return (
    <form
      onSubmit={async (e) => {
        e.preventDefault();
        const error = passwordChangeFormError(form);
        if (error) {
          setMessage(error);
          return;
        }
        setSaving(true);
        try {
          await memberApi.changeMyPassword(form);
          setForm(EMPTY_PASSWORD_FORM);
          setMessage('');
          notify?.('비밀번호를 바꿨어요.');
        } catch (err) {
          setMessage(err.message);
        } finally {
          setSaving(false);
        }
      }}
    >
      <h3>비밀번호 변경</h3>
      <div className="form-grid">
        <Field label="현재 비밀번호">
          <input
            type="password"
            autoComplete="current-password"
            required
            value={form.currentPassword}
            onChange={set('currentPassword')}
          />
        </Field>
        <Field label="새 비밀번호" hint="8자 이상 입력해 주세요.">
          <input
            type="password"
            autoComplete="new-password"
            minLength={8}
            required
            value={form.newPassword}
            onChange={set('newPassword')}
          />
        </Field>
        <Field label="새 비밀번호 확인">
          <input
            type="password"
            autoComplete="new-password"
            required
            value={form.newPasswordConfirm}
            onChange={set('newPasswordConfirm')}
          />
        </Field>
      </div>
      <Btn type="submit" disabled={saving}>
        {saving ? '변경 중…' : '비밀번호 변경'}
      </Btn>
      <p role="status">{message}</p>
    </form>
  );
}
