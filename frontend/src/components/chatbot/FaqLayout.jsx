import React from "react";
import { FaqProvider } from "./FaqContext.jsx";
import FaqChatbot from "./FaqChatbot.jsx";

// 페이지가 바뀌어도 이 컴포넌트는 유지해야 대화 상태가 이어집니다.
// isAuthenticated에는 팀 프로젝트의 실제 로그인 여부를 전달하세요.
export default function FaqLayout({
  children,
  sessionId,
  isAuthenticated = false,
}) {
  return (
    <FaqProvider sessionId={sessionId}>
      {children}
      {isAuthenticated && <FaqChatbot />}
    </FaqProvider>
  );
}
