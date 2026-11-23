import React from 'react';
import { ShieldCheck } from 'lucide-react';
import ComingSoon from '@/components/common/ComingSoon.jsx';
import { Btn, Tag } from '@/components/common/site-primitives.jsx';

// 권한 상태에 따라 관리자 콘솔 또는 접근 안내를 보여줍니다.
// [수정 11/23] 브라우저 시연 관리 도구(문의 · 회원 · 기준값 · 공공데이터 목록 · 예측 결과 · 접근 기록) 삭제
//   → 「준비 중」 안내 · 이유: 체험판 기능 삭제 결정 · 지금까지는 이 브라우저에만 저장되는 가짜 관리 기능이었음
//   관리자 API(feature/admin-1 · 2 · 3 등)가 develop에 들어오면 탭과 실제 화면을 다시 연결해요
//   (삭제한 화면 코드는 git 기록의 AdminTools.jsx · AccountForms.jsx(MemberManagement)에 있어요)
export default function AdminPage({ admin, go }) {
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
      <ComingSoon title="관리자 기능">
        문의 답변 · 회원 권한 관리 · 세법 기준값 · 예측 결과 · 접근 기록 조회는 서버 연결 작업
        중이에요.
      </ComingSoon>
    </>
  );
}
