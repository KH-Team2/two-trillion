import assert from 'node:assert/strict';
import { build } from 'esbuild';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

// 관리자 접근 기록 API 연결 검사 (F-21 · 서버 없이 가짜 fetch로 확인) · [신규 11/23]
const dir = fs.mkdtempSync(path.join(process.cwd(), 'tests/.access-log-'));
try {
  await build({
    entryPoints: ['src/api/access-log-service.js'],
    outfile: `${dir}/access-log.mjs`,
    platform: 'node',
    format: 'esm',
    bundle: true,
    logLevel: 'silent',
  });
  const { accessLogQuery, fetchAccessLogs, PAGE_SIZE } = await import(
    pathToFileURL(`${dir}/access-log.mjs`)
  );

  // 공통 봉투(ApiResponse) 모양 응답을 돌려주는 가짜 fetch · 보낸 요청은 calls에 남겨요
  const fakeFetch = (status, body, meta, calls) => async (url, init) => {
    calls.push({ url, init });
    return {
      status,
      ok: status < 400,
      json: async () =>
        status < 400
          ? { success: true, data: body, meta, error: null }
          : { success: false, data: null, meta: null, error: body },
    };
  };

  // ── 주소 만들기 · 화면 검사 ──
  assert.equal(accessLogQuery(), `?page=1&size=${PAGE_SIZE}`, '빈 조건은 빼고 쪽 정보만');
  assert.equal(
    accessLogQuery({
      startDate: '2026-11-01',
      endDate: '2026-11-23',
      ipAddress: ' 127.0.0 ',
      page: 2,
    }),
    `?startDate=2026-11-01&endDate=2026-11-23&ipAddress=127.0.0&page=2&size=${PAGE_SIZE}`,
  );
  assert.equal(
    accessLogQuery({ ipAddress: '1.2.3.4&page=999' }),
    `?ipAddress=1.2.3.4%26page%3D999&page=1&size=${PAGE_SIZE}`,
    '& = 같은 글자는 주소를 망가뜨리지 않게 바뀌어요',
  );
  assert.throws(() => accessLogQuery({ startDate: '2026-11-32x' }), /시작일/);
  assert.throws(() => accessLogQuery({ endDate: '11/23/2026' }), /종료일/);
  assert.throws(() => accessLogQuery({ startDate: '2026-11-23', endDate: '2026-11-01' }), /늦어요/);
  assert.throws(() => accessLogQuery({ page: 0 }), /쪽 번호/);
  assert.throws(() => accessLogQuery({ size: 101 }), /100건/);

  // ── 조회: data + meta를 같이 받아요 (api-client withMeta) ──
  let calls = [];
  const meta = { page: 1, size: 20, total: 35, totalPages: 2 };
  const res = await fetchAccessLogs(
    { startDate: '2026-11-01' },
    {
      fetchImpl: fakeFetch(200, [{ accessId: 9, memberId: null, result: 'FAIL' }], meta, calls),
      accessToken: 'admin.token',
    },
  );
  assert.equal(
    calls[0].url,
    `/api/admin/access-logs?startDate=2026-11-01&page=1&size=${PAGE_SIZE}`,
  );
  assert.equal(calls[0].init.method, 'GET');
  assert.equal(calls[0].init.headers.Authorization, 'Bearer admin.token');
  assert.equal(res.rows.length, 1);
  assert.deepEqual(res.meta, meta, '페이지 정보가 그대로 와요');

  // data가 비어도 빈 배열 · meta가 없으면 1쪽짜리로
  const emptyRes = await fetchAccessLogs({}, { fetchImpl: fakeFetch(200, null, null, []) });
  assert.deepEqual(emptyRes.rows, []);
  assert.equal(emptyRes.meta.totalPages, 1);

  // 관리자가 아니면 403 → 서버 문구 그대로
  await assert.rejects(
    fetchAccessLogs(
      {},
      { fetchImpl: fakeFetch(403, { code: 'FORBIDDEN', message: '접근 권한이 없어요' }, null, []) },
    ),
    (e) => e.code === 'FORBIDDEN' && e.message === '접근 권한이 없어요',
  );
  // 서버가 잘못된 날짜로 400을 주면 그대로
  await assert.rejects(
    fetchAccessLogs(
      {},
      {
        fetchImpl: fakeFetch(
          400,
          { code: 'VALIDATION', message: '요청 값 형식이 올바르지 않아요' },
          null,
          [],
        ),
      },
    ),
    (e) => e.code === 'VALIDATION',
  );
  // 서버 꺼짐 → OFFLINE
  await assert.rejects(
    fetchAccessLogs(
      {},
      {
        fetchImpl: async () => {
          throw new Error('down');
        },
      },
    ),
    (e) => e.code === 'OFFLINE' && /관리자 서버/.test(e.message),
  );
  // 화면 검사에 걸리면 서버를 부르지 않아요
  calls = [];
  await assert.rejects(
    fetchAccessLogs({ page: -1 }, { fetchImpl: fakeFetch(200, [], null, calls) }),
    /쪽 번호/,
  );
  assert.equal(calls.length, 0);

  console.log(
    'PASS: access-log-service 검색 주소 · 날짜/쪽 검사 · data+meta · 403 · 400 · OFFLINE',
  );
} finally {
  fs.rmSync(dir, { recursive: true, force: true });
}
