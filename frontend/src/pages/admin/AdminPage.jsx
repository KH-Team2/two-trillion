import React, { useState } from 'react';
import { ShieldCheck } from 'lucide-react';
import ComingSoon from '@/components/common/ComingSoon.jsx';
import { InquiryManagement } from '@/pages/admin/AdminTools.jsx';
import { Btn, Tag } from '@/components/common/site-primitives.jsx';

// 권한 상태에 따라 관리자 콘솔 또는 접근 안내를 보여줍니다.
// [수정 11/23] 체험판 기능 삭제 · 이유: 지금까지 문의 외 탭은 이 브라우저에만 저장되는 가짜 관리 기능이었음
//   - 문의 관리: 서버 API(F-18 · InquiryManagement) 그대로
//   - 회원 관리 · 공제 기준값 · 예측 결과 · 접근 기록: 「준비 중」 안내 (관리자 API가 develop에 들어오면 연결)
//   - 공공데이터 목록 탭 삭제: 브라우저에 예시 상호명 목록을 저장하던 기능 (분류 목록은 DB의 MERCHANT_LIST)
//   - 탭 상태를 App 대신 이 화면이 기억해요 (useState)
const TABS = ['문의 관리', '회원 관리', '공제 기준값', '예측 결과', '접근 기록'];
export default function AdminPage({ admin, go }) {
  const [tab, setTab] = useState(TABS[0]);
  if (!admin)
    return (
      <section className="card empty">
        <ShieldCheck size={40} />
        <h2>관리자 권한이 필요합니다</h2>
        <p>관리자 계정으로 로그인해야 접근할 수 있습니다.</p>
        <Btn onClick={() => go('dashboard')}>대시보드로</Btn>
      </section>
    );
  return (
    <>
      <div className="heading">
        <div>
          <div className="eyebrow">ADMIN WORKSPACE</div>
          <h1>관리자 콘솔</h1>
          <p>문의 · 회원 · 세법 기준값 · 예측 결과 · 접근 기록을 관리하는 화면입니다.</p>
        </div>
        <Tag>관리자</Tag>
      </div>
      <div className="tabs">
        {TABS.map((t) => (
          <button className={tab === t ? 'active' : ''} key={t} onClick={() => setTab(t)}>
            {t}
          </button>
        ))}
      </div>
      {tab === '문의 관리' ? (
        <section className="card">
          <InquiryManagement />
        </section>
      ) : (
        <ComingSoon title={tab}>{tab} 기능은 서버 연결 작업 중이에요.</ComingSoon>
      )}
    </>
  );
}
