import assert from 'node:assert/strict';
import { build } from 'esbuild';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

// 서버 알림(SSE) 연결 검사 (TT-151) · 서버 없이 가짜 fetch 로 확인
const dir = fs.mkdtempSync(path.join(process.cwd(), 'tests/.notification-'));
try {
  await build({
    entryPoints: ['src/api/notification-stream.js'],
    outfile: `${dir}/notification.mjs`,
    platform: 'node',
    format: 'esm',
    bundle: true,
    logLevel: 'silent',
  });
  const {
    parseSseBlock,
    splitSseBuffer,
    notificationFromEvent,
    openNotificationStream,
    NOTIFICATION_STREAM_PATH,
  } = await import(pathToFileURL(`${dir}/notification.mjs`));

  // 1. 덩어리 읽기
  assert.deepEqual(parseSseBlock('event:connected\ndata:ok'), { event: 'connected', data: 'ok' });
  assert.deepEqual(parseSseBlock('data:hi'), { event: 'message', data: 'hi' });
  assert.equal(parseSseBlock('event:ping'), null);

  // 2. 빈 줄 기준 자르기 · 덜 온 조각은 다음으로
  assert.deepEqual(splitSseBuffer('event:a\ndata:1\n\nevent:b\nda'), {
    blocks: ['event:a\ndata:1'],
    rest: 'event:b\nda',
  });
  assert.deepEqual(splitSseBuffer('event:a\r\ndata:1\r\n\r\n').blocks, ['event:a\r\ndata:1']);

  // 1-1. 알림 번호(id:) 읽기 (TT-159)
  assert.deepEqual(parseSseBlock('id:7\nevent:notification\ndata:{}'), {
    event: 'notification',
    data: '{}',
    id: '7',
  });

  // 3. 서버 알림(notification) → 목록 한 줄 · 문구는 서버가 준 그대로 (금액은 서버에서 넣지 않음)
  const a3 = notificationFromEvent(
    JSON.stringify({
      notificationId: 7,
      notiType: 'CALCULATION',
      content: ' 2026년 계산이 끝났어요. 눌러서 결과를 확인해 보세요. ',
      linkUrl: 'dashboard',
      readYn: 'N',
      createdAt: '2026-11-23T10:00:00',
    }),
  );
  assert.equal(a3.id, 7);
  assert.equal(a3.message, '2026년 계산이 끝났어요. 눌러서 결과를 확인해 보세요.');
  assert.equal(a3.link, 'dashboard');
  assert.equal(a3.read, false);
  assert.equal(notificationFromEvent('not json'), null);
  assert.equal(notificationFromEvent('{"content":"  "}'), null);
  // 서버 저장이 실패해 번호가 없으면 화면 임시 번호
  assert.ok(String(notificationFromEvent('{"content":"안내"}').id).startsWith('live-'));

  // 가짜 저장소 · 가짜 fetch
  const store = new Map();
  globalThis.localStorage = {
    getItem: (k) => store.get(k) ?? null,
    setItem: (k, v) => store.set(k, v),
    removeItem: (k) => store.delete(k),
  };
  const encoder = new TextEncoder();
  const streamOf = (chunks) =>
    new ReadableStream({
      start(c) {
        for (const ch of chunks) c.enqueue(encoder.encode(ch));
        c.close();
      },
    });

  // 4. 토큰이 없으면 연결하지 않음
  let calls = 0;
  const close0 = openNotificationStream({ fetchImpl: async () => (calls++, new Response('')) });
  await new Promise((r) => setTimeout(r, 10));
  assert.equal(calls, 0);
  close0();

  // 5. 토큰이 있으면 Bearer 헤더로 연결 · 쪼개져 온 이벤트도 순서대로
  store.set('hope-refund.accessToken', 'header.payload.sig');
  const seen = [];
  let req;
  const close1 = openNotificationStream({
    retryMs: 100000,
    fetchImpl: async (url, init) => {
      req = { url, init };
      return new Response(
        streamOf(['event:connected\ndata:ok\n\nevent:notifi', 'cation\ndata:{"content":"a"}\n\n']),
        { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
      );
    },
    onEvent: (event, data) => seen.push([event, data]),
  });
  await new Promise((r) => setTimeout(r, 30));
  close1();
  assert.equal(req.url, NOTIFICATION_STREAM_PATH);
  assert.equal(req.init.headers.Authorization, 'Bearer header.payload.sig');
  assert.deepEqual(seen, [
    ['connected', 'ok'],
    ['notification', '{"content":"a"}'],
  ]);

  // 6. 401 → 로그인 정보를 지우고 다시 연결하지 않음
  calls = 0;
  const close2 = openNotificationStream({
    retryMs: 5,
    fetchImpl: async () => (calls++, new Response('', { status: 401 })),
  });
  await new Promise((r) => setTimeout(r, 40));
  close2();
  assert.equal(calls, 1);
  assert.equal(store.has('hope-refund.accessToken'), false);

  // 7. 연결이 끊기면 다시 연결 · 끊은(close) 뒤에는 멈춤
  store.set('hope-refund.accessToken', 'header.payload.sig');
  calls = 0;
  const close3 = openNotificationStream({
    retryMs: 5,
    fetchImpl: async () => (calls++, new Response(streamOf(['event:connected\ndata:ok\n\n']))),
  });
  await new Promise((r) => setTimeout(r, 40));
  close3();
  const after = calls;
  assert.ok(after >= 2, `다시 연결해야 함 (${after}번)`);
  await new Promise((r) => setTimeout(r, 30));
  assert.equal(calls, after);

  // 8. 하트비트(": ping")는 이벤트로 넘기지 않고, 연결은 살아 있는 걸로 봄
  const seen8 = [];
  calls = 0;
  const close4 = openNotificationStream({
    retryMs: 100000,
    idleMs: 1000,
    fetchImpl: async () => (
      calls++,
      new Response(streamOf([': ping\n\n', 'event:connected\ndata:ok\n\n']))
    ),
    onEvent: (event, data) => seen8.push([event, data]),
  });
  await new Promise((r) => setTimeout(r, 30));
  close4();
  assert.deepEqual(seen8, [['connected', 'ok']]);

  // 9. 열린 채로 아무것도 안 오면(서버가 죽었는데 프록시가 안 닫아 줌) idleMs 뒤 끊고 다시 연결
  calls = 0;
  const close5 = openNotificationStream({
    retryMs: 5,
    idleMs: 20,
    fetchImpl: async (url, init) => {
      calls++;
      return new Response(
        new ReadableStream({
          start(c) {
            c.enqueue(encoder.encode('event:connected\ndata:ok\n\n'));
            init.signal.addEventListener('abort', () => c.error(new Error('aborted')));
          },
        }),
      );
    },
  });
  await new Promise((r) => setTimeout(r, 120));
  close5();
  assert.ok(calls >= 2, `조용하면 다시 연결해야 함 (${calls}번)`);

  // 10. 받은 알림 번호를 기억했다가 다시 연결할 때 Last-Event-ID 로 보냄 · 처음 값(lastEventId)도 받음 (TT-159)
  const sentIds = [];
  const close6 = openNotificationStream({
    retryMs: 5,
    lastEventId: 3,
    fetchImpl: async (url, init) => {
      sentIds.push(init.headers['Last-Event-ID']);
      return new Response(
        streamOf(
          sentIds.length === 1 ? ['id:5\nevent:notification\ndata:{"content":"a"}\n\n'] : [''],
        ),
      );
    },
  });
  await new Promise((r) => setTimeout(r, 40));
  close6();
  assert.equal(sentIds[0], '3');
  assert.equal(sentIds[1], '5');

  console.log('notification-stream: 11개 검사 통과');
} finally {
  fs.rmSync(dir, { recursive: true, force: true });
}
