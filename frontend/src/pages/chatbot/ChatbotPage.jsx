import React from 'react';
import ComingSoon from '@/components/common/ComingSoon.jsx';

// AI 절세 가이드 화면입니다.
// [수정 11/23] 예시 답변 상담 화면(ChatPanel · aiService) → 「준비 중」 안내
//   이유: 체험판 기능 삭제 결정 · 지금까지 답변은 서버 없이 미리 써 둔 예시 문장과 체험용 계산 금액이었음
//   AI 상담 API가 develop에 들어오면 이 자리에 실제 상담 화면을 연결해요
//   세금 용어 질문은 로그인 후 화면 오른쪽 아래 FAQ 챗봇(components/chatbot · #52)이 서버로 답해요
export default function ChatbotPage() {
  return (
    <>
      <div className="heading">
        <div>
          <div className="eyebrow">YOUR PERSONAL TAX GUIDE</div>
          <h1>어려운 세금, 쉬운 대화로</h1>
        </div>
      </div>
      <ComingSoon title="AI 절세 가이드">
        AI 상담 기능은 서버 연결 작업 중이에요. 세금 용어가 궁금하면 화면 오른쪽 아래 FAQ 챗봇에
        물어보세요.
      </ComingSoon>
    </>
  );
}
