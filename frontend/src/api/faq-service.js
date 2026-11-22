import { apiRequest } from './api-client.js';

// FAQ 원본 데이터는 FastAPI에서 관리하고 React에는 별도 JSON을 저장하지 않음
const FAQ_URL = '/api/faqs';
const FAQ_MATCHES_URL = '/api/faq-matches';

export const FAQ_FALLBACK =
  '아직 연결된 FAQ에서 답변을 찾지 못했어요. 카테고리에서 질문을 선택하거나, 다른 용어로 질문해 주세요. 개인별 세액 계산과 절세 상담은 제공하지 않아요.';

let faqCache = null;
let loadingPromise = null;

// Spring Boot 공통 API 클라이언트를 통한 FAQ 조회
async function requestFaq(url) {
  try {
    return await apiRequest(url);
  } catch (error) {
    if (error.code === 'NOT_FOUND') {
      return null;
    }
    throw error;
  }
}

// FAQ 목록 조회 및 캐싱
export async function loadFaqs() {
  if (faqCache) return faqCache;

  if (!loadingPromise) {
    loadingPromise = requestFaq(FAQ_URL)
      .then((data) => {
        if (!data || !Array.isArray(data.items)) {
          throw new Error('FAQ 목록 응답에 items 배열이 없습니다.');
        }

        faqCache = data.items;
        return faqCache;
      })
      .finally(() => {
        loadingPromise = null;
      });
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
  const trimmed = String(question ?? '').trim();

  if (!trimmed) return null;

  const data = await apiRequest(FAQ_MATCHES_URL, {
    method: 'POST',
    body: { question: trimmed },
  });

  return data?.matched ? (data.faq ?? null) : null;
}