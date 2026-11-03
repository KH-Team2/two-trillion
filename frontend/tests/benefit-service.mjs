import assert from 'node:assert/strict';
import { build } from 'esbuild';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

// 혜택 진단 API 연결 검사 (서버 없이 가짜 fetch로 확인)
const dir = fs.mkdtempSync(path.join(process.cwd(), 'tests/.benefit-'));
try {
  await build({
    entryPoints: ['src/api/benefit-service.js'],
    outfile: `${dir}/benefit.mjs`,
    platform: 'node',
    format: 'esm',
    bundle: true,
    logLevel: 'silent',
  });
  const { fetchBenefits, parseBenefitResponse, benefitStatus, PREPARE_DOCS } = await import(
    pathToFileURL(`${dir}/benefit.mjs`)
  );
  const ok = {
    taxYear: 2026,
    results: [
      {
        benefitCode: 'SME_YOUTH',
        benefitName: '중소기업 취업자 소득세 감면',
        eligible: null,
        reasons: ['회사 규모를 입력하면 진단할 수 있어요'],
        sourceNote: '국세청 신고안내 148·150p',
      },
      {
        benefitCode: 'RENT',
        benefitName: '월세 세액공제',
        eligible: false,
        reasons: ['무주택 세대주만 받을 수 있어요'],
        sourceNote: '국세청 신고안내 202p',
      },
      {
        benefitCode: 'HOUSING_SAVING',
        benefitName: '주택청약종합저축 소득공제',
        eligible: true,
        reasons: [],
        sourceNote: '국세청 신고안내 120p',
      },
    ],
  };
  const fake =
    (status, body, seen = {}) =>
    async (url, init) => {
      seen.url = url;
      seen.headers = init.headers;
      return { ok: status < 400, status, json: async () => body };
    };

  // 정상 응답: 상태 3가지를 그대로 유지
  const seen = {};
  const result = await fetchBenefits(2026, { fetchImpl: fake(200, ok, seen), memberId: '1' });
  assert.equal(seen.url, '/api/benefits/2026');
  assert.equal(seen.headers['X-Member-Id'], '1');
  assert.deepEqual(
    result.results.map((r) => r.eligible),
    [null, false, true],
  );
  assert.equal(benefitStatus(null).label, '정보 부족');
  assert.equal(benefitStatus(false).label, '비해당');
  assert.equal(benefitStatus(true).label, '해당');

  // 회원 번호가 숫자가 아니면 헤더에 넣지 않음
  const seen2 = {};
  await fetchBenefits(2026, { fetchImpl: fake(200, ok, seen2), memberId: '1; DROP' });
  assert.equal(seen2.headers['X-Member-Id'], undefined);

  // 개발용 회원번호(.env)는 개발 서버(DEV)에서만 보냄 · 빌드 파일에서는 보내지 않음
  for (const [dev, expected] of [
    [true, '7'],
    [false, undefined],
  ]) {
    const out = `${dir}/benefit-${dev}.mjs`;
    await build({
      entryPoints: ['src/api/benefit-service.js'],
      outfile: out,
      platform: 'node',
      format: 'esm',
      bundle: true,
      logLevel: 'silent',
      define: { 'import.meta.env': JSON.stringify({ DEV: dev, VITE_DEV_MEMBER_ID: '7' }) },
    });
    const mod = await import(pathToFileURL(out));
    const seenDev = {};
    await mod.fetchBenefits(2026, { fetchImpl: fake(200, ok, seenDev) });
    assert.equal(seenDev.headers['X-Member-Id'], expected);
  }

  // 서버 오류 {error, message}는 code·message 그대로
  await assert.rejects(
    fetchBenefits(2026, {
      fetchImpl: fake(404, { error: 'NOT_FOUND', message: '2026년 소득 정보가 아직 없어요' }),
    }),
    (e) => e.code === 'NOT_FOUND' && e.message === '2026년 소득 정보가 아직 없어요',
  );
  // 연결 실패 → OFFLINE
  await assert.rejects(
    fetchBenefits(2026, {
      fetchImpl: async () => {
        throw new TypeError('fetch failed');
      },
    }),
    (e) => e.code === 'OFFLINE',
  );
  // JSON이 아닌 응답(개발 프록시 오류 등) → OFFLINE
  await assert.rejects(
    fetchBenefits(2026, {
      fetchImpl: async () => ({
        ok: false,
        status: 500,
        json: async () => {
          throw new SyntaxError('x');
        },
      }),
    }),
    (e) => e.code === 'OFFLINE',
  );
  // 잘못된 연도는 요청하지 않음
  await assert.rejects(
    fetchBenefits(26, { fetchImpl: fake(200, ok) }),
    (e) => e.code === 'VALIDATION',
  );
  // 판정값이 true/false/null이 아니면 막음 (문자열 'Y' 등)
  assert.throws(
    () =>
      parseBenefitResponse({ taxYear: 2026, results: [{ benefitCode: 'RENT', eligible: 'Y' }] }),
    /형식/,
  );
  // 준비 서류 안내는 혜택 3개 모두 있음
  assert.deepEqual(Object.keys(PREPARE_DOCS), ['SME_YOUTH', 'RENT', 'HOUSING_SAVING']);
  console.log(
    'PASS: 혜택 진단 API 연결 · 상태 3가지 · 오류 · 연결 실패 · 회원 번호 헤더 · 준비 서류',
  );
} finally {
  fs.rmSync(dir, { recursive: true, force: true });
}
