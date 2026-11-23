import React from 'react';
import { Plus, Clock } from 'lucide-react';
import { settlementLabel } from '@/api/services.js';
import ComingSoon from '@/components/common/ComingSoon.jsx';
import { money, Btn, Tag } from '@/components/common/site-primitives.jsx';

// 계정 · 계산 이력을 보여 주는 마이페이지입니다.
// [수정 11/23] 체험용 코드 삭제 · 이유: 체험판 기능 삭제 결정
//   - 개인정보(성별 수정) · 비밀번호 변경 · 계정 관리(탈퇴): 브라우저에만 저장되던 가짜 기능 → 「준비 중」 안내
//     (회원정보 수정 · 탈퇴 API(F-01-04 · F-01-05)가 develop에 들어오면 연결해요)
//   - 「저장한 시뮬레이션」 탭 삭제: 브라우저에 저장한 체험용 시나리오였음
//     → 시나리오는 시뮬레이션 API가 서버에 저장하고, 비교 결과 화면의 「결과 이력」에서 다시 열어요
//   - 「이전 계산 결과」: 서버 계산 결과(source = 'API')만 보여 줘요 (체험용 결과는 빼요)
const TABS = ['개인정보', '비밀번호 변경', '이전 계산 결과', '계정 관리'];
export default function ProfilePage({
  data,
  session,
  histories,
  profileTab,
  setProfileTab,
  normalInput,
}) {
  const tab = TABS.includes(profileTab) ? profileTab : '개인정보';
  const results = histories.filter((h) => h.source === 'API');
  return (
    <>
      <div className="heading">
        <div>
          <div className="eyebrow">MY ACCOUNT</div>
          <h1>마이페이지</h1>
          <p>나의 정보와 쌓아온 기록을 한곳에서 관리해요.</p>
        </div>
      </div>
      <div className="profile-hero card">
        <span className="avatar large">희</span>
        <div>
          <h2>{data.name}님</h2>
        </div>
        <Tag>{session?.role === 'ADMIN' ? '관리자' : '일반 회원'}</Tag>
      </div>
      <div className="tabs">
        {TABS.map((t) => (
          <button className={tab === t ? 'active' : ''} onClick={() => setProfileTab(t)} key={t}>
            {t}
          </button>
        ))}
      </div>
      {tab === '이전 계산 결과' ? (
        <section className="card">
          <h3>이전 계산 결과</h3>
          {results.length === 0 ? (
            <div className="empty">
              <Clock size={30} />
              <p>아직 계산한 기록이 없어요.</p>
              <Btn secondary onClick={normalInput}>
                첫 계산 하기 <Plus size={14} />
              </Btn>
            </div>
          ) : (
            results.map((h, i) => (
              <div className="history-row" key={h.id || i}>
                <span className="iconbox">
                  <Clock size={18} />
                </span>
                <div>
                  <b>{h.date}</b>
                  <p>{h.taxYear || '연도 미기록'}년 귀속 · 계산 결과</p>
                </div>
                <b>
                  {settlementLabel(h.expectedRefund)} {money(Math.abs(h.expectedRefund))}원
                </b>
              </div>
            ))
          )}
        </section>
      ) : (
        <ComingSoon title={tab}>
          {tab === '계정 관리'
            ? '회원 탈퇴는 서버 연결 작업 중이에요.'
            : tab === '비밀번호 변경'
              ? '비밀번호 변경은 서버 연결 작업 중이에요.'
              : '회원정보 조회 · 수정은 서버 연결 작업 중이에요.'}
        </ComingSoon>
      )}
    </>
  );
}
