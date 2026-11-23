// 챗봇 화면과 버튼 표시
import React, { useEffect, useRef, useState } from "react";
import { MessageCircle, X, Send, ChevronLeft, BookOpen } from "lucide-react";
import { getFaqCategories, listFaqQuestions, loadFaqs } from "../../api/faq-service.js";
import { useFaq } from "./FaqContext.jsx";
export default function FaqChatbot() {
  const { open, setOpen, messages, busy, submit, remainingSeconds, } = useFaq();
  const [category, setCategory] = useState(null);
  const [question, setQuestion] = useState("");
  const [faqs, setFaqs] = useState([]);
  const [loadingFaqs, setLoadingFaqs] = useState(false);
  const [faqError, setFaqError] = useState(false);
  const input = useRef(null);
  const feed = useRef(null);
  const launcher = useRef(null);
  const lastBlockedQuestion = useRef(null);
  const rateLimited = remainingSeconds > 0;
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setLoadingFaqs(true);
    setFaqError(false);
    loadFaqs()
      .then((items) => { if (!cancelled) setFaqs(items); })
      .catch(() => { if (!cancelled) setFaqError(true); })
      .finally(() => { if (!cancelled) setLoadingFaqs(false); });
    return () => { cancelled = true; };
  }, [open]);
  const faqCategories = getFaqCategories(faqs);
  useEffect(() => {
    if (open) input.current?.focus();
  }, [open]);
  useEffect(() => {
    if (!open || !feed.current) return;
    const latest = [
      ...feed.current.querySelectorAll(".tax-faq-message"),
    ].at(-1);
    if (latest) {
      const top =
        latest.getBoundingClientRect().top -
        feed.current.getBoundingClientRect().top;
      feed.current.scrollTop += top - 12;
    } else feed.current.scrollTop = 0;
  }, [messages, open]);
  useEffect(() => {
    if (open && category && feed.current)
      feed.current.scrollTop = feed.current.scrollHeight;
  }, [category]);
  function close() {
    setOpen(false);
    launcher.current?.focus();
  }
  async function send(event) {
    event.preventDefault();
    if (!question.trim() || busy || rateLimited) return;
    const sentQuestion = question;
    const success = await submit({question: sentQuestion,});
    if (success) {setQuestion("");}
  }
  return (
    <div className="tax-faq-widget">
      {open && (
        <section
          id="tax-faq-panel"
          className="tax-faq-panel"
          role="dialog"
          aria-label="세금 용어 도우미"
          onKeyDown={(event) => {
            if (event.key === "Escape") close();
          }}
        >
          <header className="tax-faq-header">
            <span className="tax-faq-icon">
              <BookOpen size={20} />
            </span>
            <div>
              <h2>세금 용어 도우미</h2>
              <p>연말정산 용어 FAQ</p>
            </div>
            <button
              type="button"
              onClick={close}
              aria-label="세금 용어 도우미 닫기"
            >
              <X size={20} />
            </button>
          </header>
          <div className="tax-faq-feed" ref={feed}>
            <div className="tax-faq-bubble assistant">
              안녕하세요! 희망환급 세금 용어 도우미예요. 어려운 연말정산 용어가
              있다면 질문해 주세요.
            </div>
            <p className="tax-faq-notice">
              FAQ로 용어를 안내해요. 개인별 계산 결과 분석이나 AI 세무
              상담은 제공하지 않아요.
            </p>
            <div role="log" aria-live="polite" aria-relevant="additions">
              {messages.map((message, index) => (
                <div key={index} className={`tax-faq-message ${message.role}`}>
                  <span className="tax-faq-sender">
                    {message.role === "user" ? "나" : "세금 용어 도우미"}
                  </span>
                  <div className={`tax-faq-bubble ${message.role}`}>
                    {message.text}
                    {message.source && <small>{message.source}</small>}
                  </div>
                </div>
              ))}
            </div>
            {busy && (
              <p role="status" className="tax-faq-notice">
                답변을 불러오는 중이에요…
              </p>
            )}
            <div className="tax-faq-choices">
              {rateLimited && (
                <p className="tax-faq-notice" role="status">
                  요청 제한 중입니다. {remainingSeconds}초 후 다시 질문할 수 있어요.
                </p>
              )}
              <div className="tax-faq-choice-title">
                {category ? (
                  <>
                    <button type="button" onClick={() => setCategory(null)}>
                      <ChevronLeft size={14} />
                      전체 카테고리
                    </button>
                    <b>{category}</b>
                  </>
                ) : (
                  <b>어떤 용어가 궁금하세요?</b>
                )}
              </div>
              {loadingFaqs && <p className="tax-faq-notice">FAQ 목록을 불러오는 중이에요…</p>}
              {faqError && <p className="tax-faq-notice" role="alert">FAQ 목록을 불러오지 못했어요. 창을 닫았다가 다시 열어 주세요.</p>}
              {category
                ? listFaqQuestions(faqs, category).map((item) => (
                    <button
                      type="button"
                      key={item.id}
                      disabled={busy || loadingFaqs}
                      onClick={() => {void submit({ id: item.id });
                              setCategory(null);
                      }}
                    >
                      {item.term}
                    </button>
                  ))
                : faqCategories.map((item) => (
                    <button
                      type="button"
                      key={item}
                      onClick={() => setCategory(item)}
                    >
                      {item}
                    </button>
                  ))}
            </div>
          </div>
          <form className="tax-faq-form" onSubmit={send}>
            <label className="tax-faq-sr" htmlFor="tax-faq-input">
              질문 입력
            </label>
            <input
              id="tax-faq-input"
              ref={input}
              value={question}
              maxLength={100}
              placeholder="예: 과세표준 뜻"
              onChange={(event) => setQuestion(event.target.value)}
              onKeyDown={(event) => {
                if (
                  event.key === "Enter" &&
                  (event.nativeEvent.isComposing || event.keyCode === 229)
                )
                  event.preventDefault();
              }}
            />
            <button
              type="submit"
              disabled={!question.trim() || busy}
              aria-label="질문 전송"
            >
              <Send size={18} />
            </button>
          </form>
        </section>
      )}
      <button
        ref={launcher}
        type="button"
        className="tax-faq-launcher"
        aria-label={open ? "세금 용어 도우미 닫기" : "세금 용어 도우미 열기"}
        aria-expanded={open}
        aria-controls="tax-faq-panel"
        onClick={() => (open ? close() : setOpen(true))}
      >
        {open ? <X size={22} /> : <MessageCircle size={23} />}
        <span>세금 용어 도우미</span>
      </button>
    </div>
  );
}