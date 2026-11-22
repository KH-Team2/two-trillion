// 열림 상태, 대화 내용, 요청 처리 관리
import React, {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import { askFaq, getFaqById, FAQ_FALLBACK } from "../../api/faq-service.js";
const Context = createContext({
  openFaq: () => {},
  open: false,
  messages: [],
  busy: false,
});
export const useFaq = () => useContext(Context);
export function FaqProvider({ children, sessionId }) {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([]);
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const generation = useRef(0);
  useEffect(() => {
    generation.current += 1;
    pending.current = false;
    setOpen(false);
    setMessages([]);
    setBusy(false);
  }, [sessionId]);
  async function submit({ id, question }) {
    if (pending.current || (!id && !question?.trim())) return;
    pending.current = true;
    setBusy(true);
    const current = generation.current;
    try {
      const faq = id ? await getFaqById(id) : await askFaq(question.trim());
      if (current !== generation.current) return;
      setMessages((previous) => [
        ...previous,
        {
          role: "user",
          text: question?.trim() || faq?.term || faq?.question || "선택한 용어",
        },
        {
          role: "assistant",
          text: faq?.answer || FAQ_FALLBACK,
          source: faq?.source,
        },
      ]);
      return true;
    } catch (error) {
      if (current === generation.current) {
        const isPrivacyError =
          error?.code === "PERSONAL_INFO_DETECTED";

        setMessages((previous) => [
          ...previous,
          ...(!isPrivacyError
            ? [{ role: "user", text: question?.trim() || "선택한 용어" }]
            : []),
          {
            role: "assistant",
            text: isPrivacyError
              ? error.message
              : "답변을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
          },
        ]);
        if (isPrivacyError) {
          return false;
        }
      }
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
    <Context.Provider
      value={{ open, setOpen, messages, busy, submit, openFaq }}
    >
      {children}
    </Context.Provider>
  );
}