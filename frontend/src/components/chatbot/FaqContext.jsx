// 열림 상태, 대화 내용, 요청 처리 관리
import React, { createContext, useContext, useEffect, useRef, useState } from 'react';
import { askFaq, getFaqById, FAQ_FALLBACK } from '../../api/faq-service.js';
const Context = createContext({
  openFaq: () => {},
  open: false,
  messages: [],
  busy: false,
  remainingSeconds: 0,
});
export const useFaq = () => useContext(Context);
export function FaqProvider({ children, sessionId }) {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([]);
  const [busy, setBusy] = useState(false);
  // 요청 횟수 제한
  const [blockedUntil, setBlockedUntil] = useState(0);
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const blockedUntilRef = useRef(0);
  const pending = useRef(false);
  const generation = useRef(0);
  // 로그인 회원 변경 시 상태 초기화
  useEffect(() => {
    generation.current += 1;
    pending.current = false;
    blockedUntilRef.current = 0;
    setOpen(false);
    setMessages([]);
    setBusy(false);
    setBlockedUntil(0);
    setRemainingSeconds(0);
  }, [sessionId]);
  // 30초 카운트다운
  useEffect(() => {
    if (!blockedUntil) return;
    function updateRemaining() {
      const seconds = Math.max(0, Math.ceil((blockedUntil - Date.now()) / 1000));
      setRemainingSeconds(seconds);
      if (seconds === 0) {
        blockedUntilRef.current = 0;
        setBlockedUntil(0);
      }
    }
    updateRemaining();
    const timer = setInterval(updateRemaining, 250);
    return () => clearInterval(timer);
  }, [blockedUntil]);
  async function submit({ id, question }) {
    if (pending.current || Date.now() < blockedUntilRef.current || (!id && !question?.trim()))
      return false;
    pending.current = true;
    setBusy(true);
    const current = generation.current;
    try {
      const faq = id ? await getFaqById(id) : await askFaq(question.trim());
      if (current !== generation.current) return false;
      setMessages((previous) => [
        ...previous,
        {
          role: 'user',
          text: question?.trim() || faq?.term || faq?.question || '선택한 용어',
        },
        {
          role: 'assistant',
          text: faq?.answer || FAQ_FALLBACK,
          source: faq?.source,
        },
      ]);
      return true;
    } catch (error) {
      if (current !== generation.current) return false;
      const isPrivacyError = error?.code === 'PERSONAL_INFO_DETECTED';
      const isRateLimitError = error?.code === 'RATE_LIMIT_EXCEEDED';
      // 개인정보 차단 또는 요청 횟수 초과
      const isBlocked = isPrivacyError || isRateLimitError;
      // HTTP 429 발생 시 30초 제한 시작
      if (isRateLimitError) {
        const until = Date.now() + 30_000;

        blockedUntilRef.current = until;
        setBlockedUntil(until);
        setRemainingSeconds(30);
      }
      setMessages((previous) => [
        ...previous,
        // 개인정보 차단 및 요청 제한 질문은 표시하지 않음
        ...(!isBlocked
          ? [
              {
                role: 'user',
                text: question?.trim() || '선택한 용어',
              },
            ]
          : []),

        {
          role: 'assistant',
          text: isBlocked ? error.message : '답변을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
        },
      ]);
      return false;
    } finally {
      if (current === generation.current) {
        pending.current = false;
        setBusy(false);
      }
    }
  }
  function openFaq(id) {
    setOpen(true);
    if (id) void submit({ id });
  }
  return (
    <Context.Provider value={{ open, setOpen, messages, busy, submit, openFaq, remainingSeconds }}>
      {children}
    </Context.Provider>
  );
}
