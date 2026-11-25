import assert from 'node:assert/strict';
import { build } from 'esbuild';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

// [추가 11/24 · 이우진 · TT-154] 상담 채팅 REST · STOMP 연결 검사 · 서버 없이 가짜 요청 · 가짜 STOMP Client 로 확인
class Memory {
  map = new Map();
  getItem(k) {
    return this.map.get(k) ?? null;
  }
  setItem(k, v) {
    this.map.set(k, String(v));
  }
  removeItem(k) {
    this.map.delete(k);
  }
}
globalThis.localStorage = new Memory();
const TOKEN_KEY = 'hope-refund.accessToken';
// 가운데 칸 = {"sub":"1002","role":"USER"} (서명은 화면이 확인하지 않음 · 가짜 값)
const payload = Buffer.from(JSON.stringify({ sub: '1002', role: 'USER' })).toString('base64url');
const token = `eyJhbGciOiJIUzI1NiJ9.${payload}.signature`;

const dir = fs.mkdtempSync(path.join(process.cwd(), 'tests/.consult-'));
try {
  await build({
    entryPoints: ['src/api/consult-service.js', 'src/api/consult-socket.js'],
    outdir: dir,
    outExtension: { '.js': '.mjs' },
    platform: 'node',
    format: 'esm',
    bundle: true,
    logLevel: 'silent',
  });
  const service = await import(pathToFileURL(`${dir}/consult-service.mjs`));
  const socket = await import(pathToFileURL(`${dir}/consult-socket.mjs`));

  // 1. REST 주소 · 방식 (API 명세서 CONSULT-01 ~ 03)
  const calls = [];
  const fake = async (url, options = {}) => {
    calls.push([url, options.method || 'GET']);
    return url === '/api/consult/rooms' ? { roomId: 12 } : [];
  };
  assert.equal(await service.openConsultRoom(fake), 12);
  await service.fetchConsultMessages(12, undefined, fake);
  await service.fetchConsultMessages(12, 105, fake);
  await service.fetchConsultRooms(fake);
  assert.deepEqual(calls, [
    ['/api/consult/rooms', 'POST'],
    ['/api/consult/rooms/12/messages', 'GET'],
    ['/api/consult/rooms/12/messages?beforeId=105', 'GET'],
    ['/api/admin/consult/rooms', 'GET'],
  ]);
  // 이상한 번호로는 부르지 않음
  await assert.rejects(() => service.fetchConsultMessages('12/../1', undefined, fake), {
    code: 'VALIDATION',
  });
  await assert.rejects(() => service.fetchConsultMessages(12, 0.5, fake), { code: 'VALIDATION' });

  // 2. 글자 수 · 보내기 전 확인 (서버와 같은 기준 · 이모지 1자)
  assert.equal(service.contentLength('가😀'), 2);
  assert.equal(service.checkContent('  '), '메시지를 입력해 주세요');
  assert.equal(service.checkContent('가'.repeat(1000)), '');
  assert.equal(service.checkContent('가'.repeat(1001)), '메시지는 1000자까지 보낼 수 있어요');

  // 3. 내 회원번호는 토큰 sub (표시용) · 토큰이 깨졌으면 null
  assert.equal(service.myMemberId(token), 1002);
  assert.equal(service.myMemberId('broken'), null);
  assert.equal(service.myMemberId(null), null);

  // 4. 합치기: 같은 번호 하나만 · 번호 순
  const m = (id, extra = {}) => ({ messageId: id, senderId: 1002, senderRole: 'USER', ...extra });
  assert.deepEqual(
    service.mergeMessages([m(3), m(1)], [m(2), m(3, { content: 'new' })]).map((x) => x.messageId),
    [1, 2, 3],
  );

  // 5. 말풍선 이름 (실명 없이 역할만)
  assert.equal(service.senderLabel(m(1), 1002, false), '나');
  assert.equal(
    service.senderLabel(m(1, { senderId: 1, senderRole: 'ADMIN' }), 1002, false),
    '상담사',
  );
  assert.equal(service.senderLabel(m(1), 1, true), '고객님');
  assert.equal(service.senderLabel(m(1, { senderId: 2, senderRole: 'ADMIN' }), 1, true), '상담사');
  assert.equal(service.senderLabel(m(1, { senderId: 1, senderRole: 'ADMIN' }), 1, true), '나');
  assert.equal(service.isMine(m(1, { mine: false }), 1002), false); // 서버가 준 mine 이 먼저
  assert.equal(service.formatConsultTime('2026-11-24T15:05:00'), '11. 24. 오후 3:05');
  assert.equal(service.formatConsultTime('2026-11-24T00:07:00'), '11. 24. 오전 12:07');

  // 6. STOMP: 토큰은 CONNECT 헤더로 · 구독 2개 · 보내기 주소 · 서버가 막으면 멈춤
  assert.equal(
    socket.socketUrl({ protocol: 'http:', host: 'localhost:5173' }),
    'ws://localhost:5173/ws',
  );
  assert.equal(socket.socketUrl({ protocol: 'https:', host: 'a.b' }), 'wss://a.b/ws');

  class FakeClient {
    static last;
    constructor(config) {
      Object.assign(this, config);
      this.subs = {};
      this.published = [];
      this.active = false;
      this.connected = false;
      FakeClient.last = this;
    }
    activate() {
      this.active = true;
      this.beforeConnect();
    }
    deactivate() {
      this.active = false;
      this.connected = false;
    }
    subscribe(dest, cb) {
      this.subs[dest] = cb;
    }
    publish(frame) {
      this.published.push(frame);
    }
    // 테스트용: 서버가 연결을 받아 줌
    accept() {
      this.connected = true;
      this.onConnect();
    }
  }

  // 토큰이 없으면 연결하지 않음
  const noLogin = [];
  socket.openConsultSocket({
    roomId: 12,
    onStatus: (s) => noLogin.push(s),
    ClientImpl: FakeClient,
    url: 'ws://x/ws',
  });
  assert.deepEqual(noLogin, ['NO_LOGIN']);
  assert.equal(FakeClient.last.active, false);

  localStorage.setItem(TOKEN_KEY, token);
  const statuses = [];
  const received = [];
  const errors = [];
  let reconnects = 0;
  const conn = socket.openConsultSocket({
    roomId: 12,
    onStatus: (s) => statuses.push(s),
    onMessage: (msg) => received.push(msg),
    onError: (e) => errors.push(e),
    onReconnect: () => reconnects++,
    ClientImpl: FakeClient,
    url: 'ws://x/ws',
  });
  const client = FakeClient.last;
  assert.deepEqual(client.connectHeaders, { Authorization: `Bearer ${token}` });
  assert.equal(conn.send('안녕'), false); // 연결 전에는 안 보냄
  client.accept();
  assert.deepEqual(Object.keys(client.subs).sort(), ['/sub/consult/12', '/user/queue/errors']);
  assert.equal(conn.send('안녕하세요'), true);
  assert.deepEqual(client.published, [
    { destination: '/pub/consult/12', body: JSON.stringify({ content: '안녕하세요' }) },
  ]);
  client.subs['/sub/consult/12']({ body: JSON.stringify(m(7, { content: '안녕하세요' })) });
  client.subs['/sub/consult/12']({ body: 'not json' }); // 깨진 글은 버림
  assert.equal(received.length, 1);
  client.subs['/user/queue/errors']({
    body: JSON.stringify({ code: 'VALIDATION', message: '메시지를 입력해 주세요' }),
  });
  assert.equal(errors[0].message, '메시지를 입력해 주세요');

  // 끊겼다가 다시 연결 → RECONNECTING → CONNECTED · 끊긴 사이 메시지 다시 받기 1번
  client.onWebSocketClose();
  client.beforeConnect();
  client.accept();
  assert.equal(reconnects, 1);
  assert.deepEqual(statuses, [
    'CONNECTING',
    'CONNECTED',
    'RECONNECTING',
    'RECONNECTING',
    'CONNECTED',
  ]);

  // 서버가 막음(STOMP ERROR) → DENIED · 다시 연결하지 않음
  client.onStompError({ headers: { message: 'denied' } });
  assert.equal(statuses.at(-1), 'DENIED');
  assert.equal(client.active, false);
  client.onWebSocketClose();
  assert.equal(statuses.at(-1), 'DENIED');

  console.log('PASS 상담 채팅 REST · STOMP 연결');
} finally {
  fs.rmSync(dir, { recursive: true, force: true });
}
