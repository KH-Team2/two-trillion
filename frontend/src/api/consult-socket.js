// [추가 11/24 · 이우진 · TT-154] 상담 채팅 실시간 연결 (WebSocket + STOMP · API 명세서 WS-01 ~ 03)
//
// 흐름: /ws 연결 → CONNECT(Authorization: Bearer 토큰) → SUBSCRIBE /sub/consult/{방번호} · /user/queue/errors
//       → 보내기는 SEND /pub/consult/{방번호} {"content": "..."} → 서버가 저장한 뒤 방 구독자 모두에게 보냄(나 포함)
//
// 브라우저 WebSocket 은 처음 연결할 때 Authorization 헤더를 못 붙여요 → STOMP CONNECT 헤더에 토큰을 실어요
// (주소에 ?token= 을 붙이면 서버 로그 · 방문 기록에 남아서 쓰지 않아요 · SSE 알림과 같은 생각)
import { Client } from '@stomp/stompjs';
import { bearerHeader, readAccessToken } from './api-client.js';

export const SOCKET_PATH = '/ws';
export const ERROR_QUEUE = '/user/queue/errors'; // 나에게만 오는 오류 {code, message}
const RECONNECT_MS = 5000; // 연결이 끊기면 5초 뒤 다시 연결 (서버 재시작 · 와이파이 끊김)

export const roomTopic = (roomId) => `/sub/consult/${roomId}`;
export const sendDestination = (roomId) => `/pub/consult/${roomId}`;

/** 지금 화면 주소 기준 WebSocket 주소 (개발: Vite 프록시가 /ws 를 Spring 으로 넘김) */
export function socketUrl(loc = globalThis.location) {
  const scheme = loc?.protocol === 'https:' ? 'wss' : 'ws';
  return `${scheme}://${loc?.host}${SOCKET_PATH}`;
}

/** 받은 글(JSON) → 객체 · 깨진 글이면 null */
export function parseFrameBody(body) {
  try {
    const value = JSON.parse(body);
    return value && typeof value === 'object' ? value : null;
  } catch {
    return null;
  }
}

/**
 * 방 하나에 연결해요. 돌려주는 { send, close } 로 보내기 · 끊기
 *
 * onStatus(상태): CONNECTING · CONNECTED · RECONNECTING · DENIED(토큰 만료 · 남의 방 → 서버가 막음) · NO_LOGIN
 * onMessage(메시지): 이 방에 새 메시지가 왔을 때 (내가 보낸 것도 서버를 거쳐 다시 와요)
 * onError({code, message}): 내가 보낸 메시지가 거절됐을 때 (빈 메시지 · 1000자 초과 등)
 * onReconnect(): 끊겼다가 다시 연결됐을 때 → 끊긴 사이 메시지를 지난 메시지 API 로 다시 받아요
 */
export function openConsultSocket({
  roomId,
  onStatus,
  onMessage,
  onError,
  onReconnect,
  ClientImpl = Client,
  url = socketUrl(),
}) {
  let everConnected = false;
  let denied = false;

  const client = new ClientImpl({
    brokerURL: url,
    reconnectDelay: RECONNECT_MS,
    // 연결할 때마다 저장된 토큰을 다시 읽어요 (그 사이 다시 로그인했을 수 있어서)
    beforeConnect: () => {
      const headers = bearerHeader(readAccessToken());
      if (!headers.Authorization) {
        onStatus?.('NO_LOGIN');
        client.deactivate();
        return;
      }
      client.connectHeaders = headers;
      onStatus?.(everConnected ? 'RECONNECTING' : 'CONNECTING');
    },
    onConnect: () => {
      client.subscribe(roomTopic(roomId), (frame) => {
        const message = parseFrameBody(frame.body);
        if (message) onMessage?.(message);
      });
      client.subscribe(ERROR_QUEUE, (frame) => {
        const error = parseFrameBody(frame.body);
        onError?.(
          error?.message ? error : { code: 'SERVER_ERROR', message: '메시지를 보내지 못했어요' },
        );
      });
      onStatus?.('CONNECTED');
      if (everConnected) onReconnect?.();
      everConnected = true;
    },
    // 서버가 막음(STOMP ERROR): 토큰 없음 · 만료 · 남의 방 구독 → 다시 연결해도 같으니 멈춰요
    onStompError: () => {
      denied = true;
      onStatus?.('DENIED');
      client.deactivate();
    },
    onWebSocketClose: () => {
      if (!denied && client.active) onStatus?.('RECONNECTING');
    },
  });
  client.activate();

  return {
    /** 보내기 · 연결 전이면 false (화면이 「연결 중」 안내) */
    send(content) {
      if (!client.connected) return false;
      client.publish({ destination: sendDestination(roomId), body: JSON.stringify({ content }) });
      return true;
    },
    close() {
      client.deactivate();
    },
  };
}
