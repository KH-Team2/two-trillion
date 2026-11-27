import assert from 'node:assert/strict';
import { build } from 'esbuild';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

// 관리자 예측 결과 조회 API 연결 검사 (F-08-02 ② · 서버 없이 가짜 fetch로 확인) · [신규 11/26 고은]
// - esbuild로 서비스 파일을 node가 읽을 수 있는 한 파일로 묶은 뒤 불러와요 (admin-member-service.mjs와 같은 방식)
const dir = fs.mkdtempSync(path.join(process.cwd(), 'tests/.admin-prediction-'));
try {
  await build({
    entryPoints: ['src/api/admin-prediction-service.js'],
    outfile: `${dir}/admin-prediction.mjs`,
    platform: 'node',
    format: 'esm',
    bundle: true,
    logLevel: 'silent',
  });
  const { predictionQuery, fetchPredictions, kindOf, refundText, wonText, PREDICTION_PAGE_SIZE } =
    await import(pathToFileURL(`${dir}/admin-prediction.mjs`));

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
  assert.equal(predictionQuery(), `?page=1&size=${PREDICTION_PAGE_SIZE}`, '빈 조건은 빼고 쪽 정보만');
  assert.equal(
    predictionQuery({ keyword: ' naver ', taxYear: ' 2026 ', kind: 'SCENARIO', page: 2 }),
    `?keyword=naver&taxYear=2026&kind=SCENARIO&page=2&size=${PREDICTION_PAGE_SIZE}`,
  );
  assert.equal(
    predictionQuery({ keyword: 'a&kind=CURRENT' }),
    `?keyword=a%26kind%3DCURRENT&page=1&size=${PREDICTION_PAGE_SIZE}`,
    '& = 같은 글자는 주소를 망가뜨리지 않게 바뀌어요',
  );
  assert.throws(() => predictionQuery({ taxYear: '1999' }), /귀속 연도/);
  assert.throws(() => predictionQuery({ taxYear: '2026.5' }), /귀속 연도/);
  assert.throws(() => predictionQuery({ taxYear: 'abc' }), /귀속 연도/);
  assert.throws(() => predictionQuery({ kind: 'ALL' }), /결과 종류/);
  assert.throws(() => predictionQuery({ page: 0 }), /쪽 번호/);
  assert.throws(() => predictionQuery({ size: 101 }), /100건/);

  // ── 목록: data + meta ──
  let calls = [];
  const meta = { page: 1, size: 20, total: 2, totalPages: 1 };
  const res = await fetchPredictions(
    { kind: 'CURRENT', taxYear: 2026 },
    {
      fetchImpl: fakeFetch(
        200,
        [
          {
            predictionId: 31,
            memberId: 1002,
            email: 'te***@naver.com',
            taxYear: 2026,
            scenarioName: '현재',
            expectedRefund: 120000,
          },
        ],
        meta,
        calls,
      ),
      accessToken: 'admin.token',
    },
  );
  assert.equal(
    calls[0].url,
    `/api/admin/predictions?taxYear=2026&kind=CURRENT&page=1&size=${PREDICTION_PAGE_SIZE}`,
  );
  assert.equal(calls[0].init.method, 'GET');
  assert.equal(calls[0].init.headers.Authorization, 'Bearer admin.token');
  assert.equal(res.rows[0].email, 'te***@naver.com');
  assert.deepEqual(res.meta, meta);

  // data · meta가 비어도 화면이 깨지지 않게
  const emptyRes = await fetchPredictions({}, { fetchImpl: fakeFetch(200, null, null, []) });
  assert.deepEqual(emptyRes.rows, []);
  assert.equal(emptyRes.meta.totalPages, 1);

  // 화면 검사에 걸리면 서버를 부르지 않아요
  calls = [];
  await assert.rejects(
    fetchPredictions({ taxYear: '1999' }, { fetchImpl: fakeFetch(200, [], null, calls) }),
    /귀속 연도/,
  );
  assert.equal(calls.length, 0);

  // 관리자가 아니면 403 · 서버 꺼짐 → OFFLINE
  await assert.rejects(
    fetchPredictions(
      {},
      { fetchImpl: fakeFetch(403, { code: 'FORBIDDEN', message: '접근 권한이 없어요' }, null, []) },
    ),
    (e) => e.code === 'FORBIDDEN',
  );
  await assert.rejects(
    fetchPredictions(
      {},
      {
        fetchImpl: async () => {
          throw new Error('down');
        },
      },
    ),
    (e) => e.code === 'OFFLINE' && /관리자 서버/.test(e.message),
  );

  // ── 화면 글자 ──
  assert.equal(kindOf({ scenarioName: '현재' }), 'CURRENT');
  assert.equal(kindOf({ scenarioName: '월세 추가' }), 'SCENARIO');
  assert.equal(refundText(120000), '120,000원 환급');
  assert.equal(refundText(-30000), '30,000원 추가 납부', '음수 = 추가 납부');
  assert.equal(refundText(0), '0원 환급');
  assert.equal(refundText(null), '-');
  assert.equal(wonText(35000000), '35,000,000원');
  assert.equal(wonText(null), '-', '입력값 행이 없으면 총급여 null');

  console.log(
    'PASS: admin-prediction-service 검색 주소 · 연도/종류 검사 · data+meta · 403 · OFFLINE · 환급/추가 납부 글자',
  );
} finally {
  fs.rmSync(dir, { recursive: true, force: true });
}
