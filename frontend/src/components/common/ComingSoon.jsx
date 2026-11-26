import React from 'react';

// [추가 11/23] 서버 API가 아직 없는 화면에 띄우는 「준비 중」 안내 카드예요.
// 체험판(브라우저에만 저장하던 가짜 기능)을 지우면서, 그 자리에 이 안내를 보여 줘요.
// API가 develop에 들어오면 이 컴포넌트 대신 실제 화면을 연결하면 돼요.
// title: 카드 제목 · children: 설명 문구(없으면 기본 문구)
export default function ComingSoon({ title, children }) {
  return (
    <section className="card coming-soon" role="status">
      <span className="tag gray">준비 중</span>
      <h2>{title}</h2>
      <p className="muted">{children || '서버 연결 작업 중인 기능이에요. 조금만 기다려 주세요.'}</p>
    </section>
  );
}
