// FAQ 원본 데이터는 FastAPI에서 관리합니다. React에는 별도 JSON을 저장하지 않습니다.
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

export function getFaqCategories(items) {
  return [...new Set(items.map((item) => item.category))];
}

export function listFaqQuestions(items, category) {
  return items
    .filter((item) => item.category === category)
    .map(({ id, question }) => ({ id, question }));
}

export async function getFaqById(id) {
  if (!id) return null;
  return requestFaq(`${FAQ_URL}/${encodeURIComponent(id)}`);
}

// AI 분류 연동 전까지 질문/별칭의 정확한 일치만 처리합니다.
export async function askFaq(question) {
  const key = normalize(question);
  if (!key) return null;
  const items = await loadFaqs();
  return items.find((item) =>
    [item.question, ...(item.aliases ?? [])].some((value) => normalize(value) === key)
  ) ?? null;
}

// TermHelp에서 기존 동기식 호출을 유지할 수 있도록 캐시를 사용합니다.
// FAQ 목록이 로드되기 전에는 undefined를 반환합니다.
export function faqIdForTerm(term) {
  const key = normalize(term);
  return faqCache?.find((item) =>
    [item.question, ...(item.aliases ?? [])].some((value) => normalize(value) === key)
  )?.id;
}
