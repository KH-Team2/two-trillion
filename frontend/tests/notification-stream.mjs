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
    calculationMessage,
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

  // 3. 토스트 문구 · 환급 / 추가 납부 구분
  assert.equal(
    calculationMessage('{"taxYear":2026,"predictionId":75,"expectedRefund":446240}'),
    '2026년 계산이 끝났어요. 예상 환급액 446,240원이에요.',
  );
  assert.equal(
    calculationMessage('{"taxYear":2026,"expectedRefund":-12000}'),
    '2026년 계산이 끝났어요. 예상 추가 납부액 12,000원이에요.',
  );
  assert.equal(calculationMessage('not json'), null);
  assert.equal(calculationMessage('{"taxYear":2026}'), null);

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
        streamOf(['event:connected\ndata:ok\n\nevent:calcul', 'ation\ndata:{"taxYear":2026}\n\n']),
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
    ['calculation', '{"taxYear":2026}'],
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

  console.log('notification-stream: 7개 검사 통과');
} finally {
  fs.rmSync(dir, { recursive: true, force: true });
}
