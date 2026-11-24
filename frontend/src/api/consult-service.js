// [추가 11/24 · 이우진 · TT-154] 관리자 상담 채팅 REST 호출 (API 명세서 CONSULT-01 ~ 03 · 기능명세서 F-07-03)
// 실시간 메시지(보내기 · 받기)는 consult-socket.js (STOMP) · 여기는 방 열기 · 지난 메시지 · 관리자 방 목록만
// 누가 보냈는지는 서버가 로그인 토큰에서만 꺼내요 → 화면은 회원번호를 보내지 않아요
import { apiRequest, apiError, readAccessToken } from './api-client.js';

export const CONTENT_MAX = 1000; // CONSULT_MESSAGE.CONTENT 1000자 (서버도 같은 기준으로 다시 확인)
export const PAGE_SIZE = 30; // 지난 메시지 한 번에 30개 (서버 ConsultService.PAGE_SIZE)

/** CONSULT-01 상담 요청: 내 방이 있으면 그 방, 없으면 새로 만듦 → 방 번호 */
export async function openConsultRoom(request = apiRequest) {
  const data = await request('/api/consult/rooms', { method: 'POST' });
  return data.roomId;
}

/** CONSULT-02 지난 메시지: beforeId 보다 오래된 30개 (없으면 가장 최근 30개) · 오래된 순 */
export async function fetchConsultMessages(roomId, beforeId, request = apiRequest) {
  checkId(roomId, '상담 방 번호');
  if (beforeId !== undefined && beforeId !== null) checkId(beforeId, 'beforeId');
  const query = beforeId ? `?beforeId=${beforeId}` : '';
  return request(`/api/consult/rooms/${roomId}/messages${query}`);
}

/** CONSULT-03 관리자 상담 방 목록 (최근 메시지 순 · 아이디 일부 가림) */
export async function fetchConsultRooms(request = apiRequest) {
  return request('/api/admin/consult/rooms');
}

// 주소에 들어가는 번호는 1 이상 정수만 (이상한 값으로 다른 주소를 부르지 않게)
function checkId(value, label) {
  if (!Number.isInteger(value) || value <= 0) {
    throw apiError('VALIDATION', `${label}를 확인해 주세요.`);
  }
}

/**
 * 내 회원번호 (말풍선을 「나」로 그릴지 정할 때만 씀)
 * 로그인 토큰(JWT) 가운데 칸의 sub 값을 읽기만 해요 · 서명 확인은 서버가 하고, 이 값을 서버로 보내지도 않아요
 * 토큰이 없거나 모양이 다르면 null
 */
export function myMemberId(token = readAccessToken()) {
  try {
    const payload = String(token || '').split('.')[1];
    if (!payload) return null;
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
    const id = Number(json.sub);
    return Number.isInteger(id) && id > 0 ? id : null;
  } catch {
    return null;
  }
}

/** 글자 수 (서버와 같이 이모지처럼 두 칸짜리 글자도 1자로 셈) */
export function contentLength(text) {
  return [...String(text ?? '')].length;
}

/** 보내기 전 확인 → 문제가 있으면 안내 문구 · 괜찮으면 '' (서버 문구와 같게) */
export function checkContent(text) {
  if (!String(text ?? '').trim()) return '메시지를 입력해 주세요';
  if (contentLength(text) > CONTENT_MAX) return `메시지는 ${CONTENT_MAX}자까지 보낼 수 있어요`;
  return '';
}

/**
 * 메시지 목록 합치기: 같은 번호는 하나만 · 번호 순(오래된 것 위)
 * 실시간으로 받은 메시지와 다시 불러온 지난 메시지가 겹칠 수 있어서 (다시 연결된 뒤 등)
 */
export function mergeMessages(current, incoming) {
  const byId = new Map();
  for (const message of [...current, ...incoming]) {
    if (Number.isInteger(message?.messageId)) byId.set(message.messageId, message);
  }
  return [...byId.values()].sort((a, b) => a.messageId - b.messageId);
}

/**
 * 말풍선 위 이름 (실명 미수집 → 역할만)
 * 회원 화면: 나 · 상담사 / 관리자 화면: 나 · 고객님 · 상담사(다른 관리자)
 */
export function senderLabel(message, myId, admin) {
  if (isMine(message, myId)) return '나';
  if (message.senderRole === 'ADMIN') return '상담사';
  return admin ? '고객님' : '상대';
}

/** 내가 보낸 메시지인지 (지난 메시지는 서버가 준 mine · 실시간 메시지는 보낸 사람 번호로) */
export function isMine(message, myId) {
  if (typeof message?.mine === 'boolean') return message.mine;
  return myId !== null && message?.senderId === myId;
}

/** 서버 시각 문자열('2026-11-24T15:05:00') → '11. 24. 오후 3:05' · 모양이 다르면 그대로 */
export function formatConsultTime(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(String(value || ''));
  if (!match) return value || '';
  const [, , month, day, hour, minute] = match;
  const h = Number(hour);
  const half = h < 12 ? '오전' : '오후';
  const h12 = h % 12 === 0 ? 12 : h % 12;
  return `${Number(month)}. ${Number(day)}. ${half} ${h12}:${minute}`;
}
