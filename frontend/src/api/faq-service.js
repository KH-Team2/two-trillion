import { apiRequest } from "./api-client.js";
// FAQ 원본 데이터는 FastAPI 관리. React에는 별도 JSON을 저장하지 않음.
// Spring Boot API 호출과 FAQ 목록 가공
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "http://localhost:8080").replace(/\/$/, "");
const FAQ_URL = `${API_BASE_URL}/api/chatbot/faq`;

const normalize = (text) =>
  String(text ?? "")
    .normalize("NFKC")
    .replace(/[\s?!？。，.,]/g, "")
    .toLowerCase();

export const FAQ_FALLBACK =
  "아직 연결된 FAQ에서 답변을 찾지 못했어요. 카테고리에서 질문을 선택하거나, 다른 용어로 질문해 주세요. 개인별 세액 계산과 절세 상담은 제공하지 않아요.";

let faqCache = null;
let loadingPromise = null;

// Spring Boot에 fetch() 요청
async function requestFaq(url) {
  const response = await fetch(url, { headers: { Accept: "application/json" } });
  if (!response.ok) {
    if (response.status === 404) return null;
    throw new Error(`FAQ API 오류 (${response.status})`);
  }
  const result = await response.json();
  if (!result || result.success !== true) {
    throw new Error("FAQ API 응답 형식이 올바르지 않습니다.");
  }
  return result.data;
}

// FastAPI GET /faq 응답: { total: 36, items: [...] }
// Spring Boot가 감싼 응답: { success: true, data: { total, items }, ... }
// FAQ 목록 조회 및 캐싱
export async function loadFaqs() {
  if (faqCache) return faqCache;
  if (!loadingPromise) {
    loadingPromise = requestFaq(FAQ_URL)
      .then((data) => {
        if (!data || !Array.isArray(data.items)) {
          throw new Error("FAQ 목록 응답에 items 배열이 없습니다.");
        }
        faqCache = data.items;
        return faqCache;
      })
      .finally(() => { loadingPromise = null; });
  }
  return loadingPromise;
}

// FAQ 목록에서 중복 없는 카테고리 추출
export function getFaqCategories(items) {
  return [...new Set(items.map((item) => item.category))];
}

// 선택한 카테고리의 용어 목록 생성
export function listFaqQuestions(items, category) {
  return items
    .filter((item) => item.category === category)
    .map(({ id, term }) => ({ id, term }));
}

// FAQ ID로 상세 답변 조회
export async function getFaqById(id) {
  if (!id) return null;
  return requestFaq(`${FAQ_URL}/${encodeURIComponent(id)}`);
}

// 자유 입력 질문을 Spring Boot → FastAPI → Gemma 3로 전달
export async function askFaq(question) {
  const trimmed = String(question ?? "").trim();
  if (!trimmed) return null;

  const response = await fetch(`${FAQ_URL}/ask`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({ question: trimmed }),
  });

  if (!response.ok) {
    throw new Error(`FAQ 질문 분류 API 오류 (${response.status})`);
  }

  const result = await response.json();

  if (!result || result.success !== true || !result.data) {
    throw new Error("FAQ 질문 분류 API 응답 형식이 올바르지 않습니다.");
  }

  return result.data.matched ? result.data.faq ?? null : null;
}
