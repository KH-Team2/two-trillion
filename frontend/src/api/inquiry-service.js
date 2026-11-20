// 고객센터 문의 API 연결 (F-17 · F-07-01 · S-07) · [신규 11/20]
// - POST /api/inquiries        : 문의 작성 (유형 · 제목 · 내용)
// - GET  /api/inquiries        : 내 문의 목록 (최신순 · 로그인한 회원 것만)
// - GET  /api/inquiries/{id}   : 내 문의 하나 (남의 문의 · 없는 문의면 404 NOT_FOUND)
// 회원번호는 보내지 않아요. 서버가 로그인 토큰(JWT)에서 꺼내요 (api-client.js가 토큰을 헤더에 붙임).
// [추가 11/20] 관리자 답변 (F-18 · F-07-02 · S-08 · D-22) · /api/admin/** 는 관리자(ROLE=ADMIN) 토큰만 통과 (아니면 403)
// - GET /api/admin/inquiries?status=      : 전체 문의 목록 (최신순 · 상태로 거르기 · 비우면 전체)
// - PUT /api/admin/inquiries/{id}/answer  : [확인 중] {status:'CHECKING'} · 답변 등록 {status:'ANSWERED', answer}
//   이미 답변 완료된 문의를 다시 바꾸면 409 ALREADY_ANSWERED
import { apiError, apiRequest } from './api-client.js';

// 문의 유형 · 처리 상태 코드 → 화면 이름 (서버 · DB 코드값과 같음 · D-12 · D-22)
export const INQUIRY_TYPE_LABEL = { ERROR: '오류', QUESTION: '문의', ETC: '기타' };
export const INQUIRY_STATUS_LABEL = { WAITING: '접수', CHECKING: '확인 중', ANSWERED: '답변 완료' };

// 확인 중일 때 보여 줄 임시 안내 (DB에 저장하지 않는 화면 고정 문구 · D-22)
export const CHECKING_MESSAGE = '확인 후 답변 드리겠습니다.';

// 제목 최대 글자 수 (서버 @Size(max = 66)와 같음 · DB 200바이트 ÷ 한글 3바이트)
export const TITLE_MAX = 66;

const OFFLINE_MESSAGE = '고객센터 서버에 연결할 수 없어요.';

/**
 * 화면 입력값 → 서버 InquiryCreateRequestDto 이름 (inquiryType · title · content).
 * 서버도 같은 검사를 하지만, 서버에 보내기 전에 화면에서 먼저 막아 안내해요.
 * trim(): 앞뒤 띄어쓰기를 지움 → 띄어쓰기만 넣은 경우도 「빈칸」으로 봄 (서버 @NotBlank와 같은 기준)
 */
export function inquiryRequest({ inquiryType, title, content } = {}) {
  const type = String(inquiryType ?? '').trim();
  const cleanTitle = String(title ?? '').trim();
  const cleanContent = String(content ?? '').trim();
  if (!INQUIRY_TYPE_LABEL[type]) throw apiError('VALIDATION', '문의 유형을 선택해 주세요');
  if (!cleanTitle) throw apiError('VALIDATION', '제목을 입력해 주세요');
  if (cleanTitle.length > TITLE_MAX) {
    throw apiError('VALIDATION', `제목은 ${TITLE_MAX}자 이내로 입력해 주세요`);
  }
  if (!cleanContent) throw apiError('VALIDATION', '문의 내용을 입력해 주세요');
  return { inquiryType: type, title: cleanTitle, content: cleanContent };
}

/** 문의 작성 · 성공하면 서버가 data: null을 줘요 (작성 뒤 목록을 다시 불러와요) */
export function createInquiry(form, options = {}) {
  return apiRequest('/api/inquiries', {
    ...options,
    method: 'POST',
    body: inquiryRequest(form),
    offlineMessage: OFFLINE_MESSAGE,
  });
}

/** 내 문의 목록 (없으면 빈 배열) */
export async function fetchInquiries(options = {}) {
  const rows = await apiRequest('/api/inquiries', { ...options, offlineMessage: OFFLINE_MESSAGE });
  return Array.isArray(rows) ? rows : [];
}

/** 내 문의 하나 (최신 답변 상태를 다시 받아 올 때) */
export function fetchInquiry(inquiryId, options = {}) {
  // 번호가 양의 정수가 아니면 주소에 넣지 않아요 (이상한 주소로 요청이 가지 않게)
  if (!Number.isInteger(Number(inquiryId)) || Number(inquiryId) <= 0) {
    throw apiError('VALIDATION', '문의 번호를 확인해 주세요');
  }
  return apiRequest(`/api/inquiries/${encodeURIComponent(inquiryId)}`, {
    ...options,
    offlineMessage: OFFLINE_MESSAGE,
  });
}

// ── F-18 관리자 ──

/** [추가 11/20] 관리자용 전체 문의 목록 · status가 비면 전체 */
export async function fetchAdminInquiries(status = '', options = {}) {
  // 상태 코드가 셋 중 하나가 아니면 조건 없이(전체) 불러와요
  const query = INQUIRY_STATUS_LABEL[status] ? `?status=${encodeURIComponent(status)}` : '';
  const rows = await apiRequest(`/api/admin/inquiries${query}`, {
    ...options,
    offlineMessage: OFFLINE_MESSAGE,
  });
  return Array.isArray(rows) ? rows : [];
}

/**
 * [추가 11/20] 관리자 [확인 중] 처리 · 답변 등록 (서버 InquiryAnswerRequestDto 이름: status · answer)
 * - CHECKING: 답변 없이 상태만 바꿔요 (문의자 화면에는 「확인 후 답변 드리겠습니다.」)
 * - ANSWERED: 답변이 꼭 있어야 해요 (서버도 같은 검사 · DB CK_INQUIRY_ANSWER)
 * 답변자(관리자 번호)는 보내지 않아요. 서버가 로그인 토큰에서 꺼내요.
 */
export function answerRequest({ status, answer } = {}) {
  if (status === 'CHECKING') return { status };
  if (status !== 'ANSWERED') throw apiError('VALIDATION', '바꿀 상태를 확인해 주세요');
  const cleanAnswer = String(answer ?? '').trim();
  if (!cleanAnswer) throw apiError('VALIDATION', '답변을 입력해 주세요');
  return { status, answer: cleanAnswer };
}

export function updateInquiryAnswer(inquiryId, form, options = {}) {
  if (!Number.isInteger(Number(inquiryId)) || Number(inquiryId) <= 0) {
    throw apiError('VALIDATION', '문의 번호를 확인해 주세요');
  }
  return apiRequest(`/api/admin/inquiries/${encodeURIComponent(inquiryId)}/answer`, {
    ...options,
    method: 'PUT',
    body: answerRequest(form),
    offlineMessage: OFFLINE_MESSAGE,
  });
}

/** 답변 칸에 보일 문구 · 확인 중이면 고정 안내, 답변 완료면 답변, 그 밖(접수)은 대기 안내 */
export function answerText(inquiry) {
  if (inquiry?.status === 'CHECKING') return CHECKING_MESSAGE;
  if (inquiry?.status === 'ANSWERED' && inquiry.answer) return inquiry.answer;
  return '접수되었습니다. 답변을 기다리고 있어요.';
}

/**
 * 서버 날짜 → 화면 표시 (예: "2026. 11. 20. 오후 5:30:00")
 * 서버는 LocalDateTime을 "2026-11-20T17:30:00" 모양 글자로 보내요. 비었거나 읽을 수 없으면 빈 글자.
 */
export function formatDateTime(value) {
  if (!value) return '';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleString('ko-KR');
}
