// 서버 알림(SSE) 연결 · 2차 미션 TT-151
// 서버: GET /api/notifications/stream (TT-150) · 응답은 text/event-stream 이라 공통 봉투가 아니에요
//
// 브라우저 기본 EventSource 는 Authorization 헤더를 못 붙여요.
// 주소에 토큰을 붙이면(?token=) 서버 로그 · 방문 기록에 남으니, fetch 로 직접 읽어서 다른 API 와 같은 Bearer 헤더를 써요.
import { bearerHeader, expireLogin, readAccessToken } from './api-client.js';
import { toAlert } from './notification-service.js';

export const NOTIFICATION_STREAM_PATH = '/api/notifications/stream';
const RETRY_MS = 5000; // 연결이 끊기면 5초 뒤 다시 연결 (서버 재시작 · 30분 시간 초과)
// 서버는 25초마다 하트비트(": ping")를 보내요 → 60초 동안 아무것도 안 오면 연결이 죽은 걸로 보고 다시 연결
// (서버가 꺼져도 중간 프록시가 연결을 안 닫아 주면 브라우저는 끊긴 걸 모르기 때문)
const IDLE_MS = 60000;

/** SSE 덩어리 하나("id:번호\nevent:이름\ndata:내용") → { event, data, id? } · data 가 없으면 null */
export function parseSseBlock(block) {
  let event = 'message';
  let id;
  const data = [];
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) event = line.slice(6).trim();
    else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
    else if (line.startsWith('id:')) id = line.slice(3).trim(); // [추가 11/23 · TT-159] 알림 번호
  }
  if (!data.length) return null;
  return id ? { event, data: data.join('\n'), id } : { event, data: data.join('\n') };
}

/** 받은 글을 빈 줄 기준으로 잘라서, 완성된 덩어리(blocks)와 아직 덜 온 조각(rest)으로 나눠요 */
export function splitSseBuffer(buffer) {
  const parts = buffer.split(/\r?\n\r?\n/);
  const rest = parts.pop();
  return { blocks: parts.filter((p) => p.trim()), rest };
}

/**
 * [수정 11/23 · 이우진 · TT-159] 서버 알림(notification 이벤트 · data = 저장된 알림 JSON) → 종 아이콘 목록 한 줄 · 모양이 다르면 null
 * 문구는 서버가 만든 그대로 씀 (서버에서 금액 등 금융 정보를 넣지 않음 · 11/20 계산 완료 · 마감 안내 따로 만들던 문구를 서버로 옮김)
 */
export function notificationFromEvent(data) {
  try {
    const n = JSON.parse(data);
    if (typeof n?.content !== 'string' || !n.content.trim()) return null;
    return toAlert(n);
  } catch {
    return null;
  }
}

/**
 * 알림 연결을 열고, 이벤트가 올 때마다 onEvent(이름, 내용)을 불러요.
 * 돌려주는 함수를 부르면 연결을 끊어요 (로그아웃 · 화면 닫힘).
 *
 * - 로그인 토큰이 없으면 연결하지 않아요.
 * - 401: 토큰 만료 · 로그아웃 → 다른 API 와 같이 로그인 정보를 지우고 멈춰요.
 * - 그 밖의 4xx: 서버에 알림 기능이 없음 등 → 다시 시도하지 않아요.
 * - 연결 끊김 · 5xx · 네트워크 오류: retryMs 뒤 다시 연결해요.
 * - idleMs 동안 아무것도 안 오면(하트비트도 없음) 연결이 죽은 걸로 보고 끊은 뒤 다시 연결해요.
 * - [추가 11/23 · TT-159] 마지막으로 받은 알림 번호를 기억했다가, 다시 연결할 때 Last-Event-ID 헤더로 보내요
 *   → 끊겨 있던 동안 생긴 알림을 서버가 다시 보내 줘요. lastEventId 로 처음 값을 줄 수 있어요(목록을 먼저 불러온 경우).
 */
export function openNotificationStream({
  onEvent,
  fetchImpl = globalThis.fetch,
  retryMs = RETRY_MS,
  idleMs = IDLE_MS,
  lastEventId,
} = {}) {
  let lastId =
    lastEventId != null && /^\d+$/.test(String(lastEventId)) ? String(lastEventId) : null;
  let closed = false;
  let controller = null;
  let timer = null;
  let idleTimer = null;

  const retry = () => {
    if (!closed) timer = setTimeout(connect, retryMs);
  };

  async function connect() {
    const headers = { Accept: 'text/event-stream', ...bearerHeader(readAccessToken()) };
    if (!headers.Authorization) return;
    if (lastId) headers['Last-Event-ID'] = lastId;
    controller = new AbortController();
    const current = controller;
    // 받은 게 있을 때마다 다시 잼 · idleMs 넘게 조용하면 이 연결을 끊음 → 아래 catch → 다시 연결
    const resetIdle = () => {
      clearTimeout(idleTimer);
      idleTimer = setTimeout(() => current.abort(), idleMs);
    };
    resetIdle();
    try {
      const response = await fetchImpl(NOTIFICATION_STREAM_PATH, {
        headers,
        signal: controller.signal,
      });
      if (response.status === 401) {
        expireLogin();
        return;
      }
      if (response.status >= 400 && response.status < 500) return;
      if (!response.ok || !response.body) {
        retry();
        return;
      }
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      while (!closed) {
        const { value, done } = await reader.read();
        if (done) break;
        resetIdle();
        buffer += decoder.decode(value, { stream: true });
        const { blocks, rest } = splitSseBuffer(buffer);
        buffer = rest;
        for (const block of blocks) {
          const message = parseSseBlock(block);
          if (!message) continue;
          if (message.id && /^\d+$/.test(message.id)) lastId = message.id;
          onEvent?.(message.event, message.data);
        }
      }
    } catch {
      // 끊음(abort · 조용함) · 네트워크 오류 → 아래에서 다시 연결 (직접 끊은 경우는 closed 라서 안 함)
    }
    clearTimeout(idleTimer);
    retry();
  }

  connect();
  return () => {
    closed = true;
    clearTimeout(timer);
    clearTimeout(idleTimer);
    controller?.abort();
  };
}
