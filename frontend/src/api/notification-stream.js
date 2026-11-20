// 서버 알림(SSE) 연결 · 2차 미션 TT-151
// 서버: GET /api/notifications/stream (TT-150) · 응답은 text/event-stream 이라 공통 봉투가 아니에요
//
// 브라우저 기본 EventSource 는 Authorization 헤더를 못 붙여요.
// 주소에 토큰을 붙이면(?token=) 서버 로그 · 방문 기록에 남으니, fetch 로 직접 읽어서 다른 API 와 같은 Bearer 헤더를 써요.
import { bearerHeader, expireLogin, readAccessToken } from './api-client.js';

export const NOTIFICATION_STREAM_PATH = '/api/notifications/stream';
const RETRY_MS = 5000; // 연결이 끊기면 5초 뒤 다시 연결 (서버 재시작 · 30분 시간 초과)
// 서버는 25초마다 하트비트(": ping")를 보내요 → 60초 동안 아무것도 안 오면 연결이 죽은 걸로 보고 다시 연결
// (서버가 꺼져도 중간 프록시가 연결을 안 닫아 주면 브라우저는 끊긴 걸 모르기 때문)
const IDLE_MS = 60000;

/** SSE 덩어리 하나("event:이름\ndata:내용") → { event, data } · data 가 없으면 null */
export function parseSseBlock(block) {
  let event = 'message';
  const data = [];
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) event = line.slice(6).trim();
    else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
  }
  return data.length ? { event, data: data.join('\n') } : null;
}

/** 받은 글을 빈 줄 기준으로 잘라서, 완성된 덩어리(blocks)와 아직 덜 온 조각(rest)으로 나눠요 */
export function splitSseBuffer(buffer) {
  const parts = buffer.split(/\r?\n\r?\n/);
  const rest = parts.pop();
  return { blocks: parts.filter((p) => p.trim()), rest };
}

/** 계산 완료 알림(data JSON) → 토스트 문구 · 환급 / 추가 납부를 글자로 구분 (TT-126) · 모양이 다르면 null */
export function calculationMessage(data) {
  try {
    const { taxYear, expectedRefund } = JSON.parse(data);
    if (!Number.isInteger(taxYear) || typeof expectedRefund !== 'number') return null;
    const won = Math.abs(expectedRefund).toLocaleString('ko-KR');
    return expectedRefund >= 0
      ? `${taxYear}년 계산이 끝났어요. 예상 환급액 ${won}원이에요.`
      : `${taxYear}년 계산이 끝났어요. 예상 추가 납부액 ${won}원이에요.`;
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
 */
export function openNotificationStream({
  onEvent,
  fetchImpl = globalThis.fetch,
  retryMs = RETRY_MS,
  idleMs = IDLE_MS,
} = {}) {
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
          if (message) onEvent?.(message.event, message.data);
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
