import assert from 'node:assert/strict';
import { build } from 'esbuild';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

// 관리자 세법 기준값 API 연결 검사 (F-19 · 서버 없이 가짜 fetch로 확인) · [신규 11/26 고은]
const dir = fs.mkdtempSync(path.join(process.cwd(), 'tests/.tax-criteria-'));
try {
  await build({
    entryPoints: ['src/api/tax-criteria-service.js'],
    outfile: `${dir}/tax-criteria.mjs`,
    platform: 'node',
    format: 'esm',
    bundle: true,
    logLevel: 'silent',
  });
  const s = await import(pathToFileURL(`${dir}/tax-criteria.mjs`));

  // 공통 봉투(ApiResponse) 모양 응답을 돌려주는 가짜 fetch · 보낸 요청은 calls에 남겨요
  const fakeFetch =
    (status, body, calls = []) =>
    async (url, init) => {
      calls.push({ url, init });
      return {
        status,
        ok: status < 400,
        json: async () =>
          status < 400
            ? { success: true, data: body, meta: null, error: null }
            : { success: false, data: null, meta: null, error: body },
      };
    };

  // ── 조회 ──
  let calls = [];
  const res = await s.fetchTaxCriteria(2026, {
    fetchImpl: fakeFetch(
      200,
      { taxYear: 2026, years: [2026, 2025], deductionTypes: [{ typeId: 1 }] },
      calls,
    ),
  });
  assert.equal(calls[0].url, '/api/admin/tax-criteria?taxYear=2026');
  assert.deepEqual(res.years, [2026, 2025]);
  assert.deepEqual(res.taxBrackets, [], '비어 온 표는 빈 배열');
  calls = [];
  await s.fetchTaxCriteria('', { fetchImpl: fakeFetch(200, { years: [] }, calls) });
  assert.equal(calls[0].url, '/api/admin/tax-criteria', '연도를 비우면 서버가 최근 연도를 고름');
  await assert.rejects(s.fetchTaxCriteria(1999), /연도/);

  // ── 폼 ↔ 요청 본문 ──
  const row = {
    typeId: 12,
    taxYear: 2026,
    typeCode: 'CARD_MARKET',
    typeName: '전통시장',
    deductionKind: 'CARD',
    rate: 40,
    limitAmount: null,
    salaryMin: 0,
    salaryMax: null,
    categoryId: 3,
    payMethod: null,
    sourceNote: '조특법',
  };
  const form = s.formFromRow(s.TARGET.DEDUCTION_TYPE, row);
  assert.equal(form.limitAmount, '', '비어 있는 값은 빈칸');
  const edit = s.toRequestBody(s.TARGET.DEDUCTION_TYPE, { ...form, limitAmount: '1,000,000' }, row);
  assert.equal(edit.id, 12);
  assert.equal(edit.limitAmount, 1000000, '쉼표는 지우고 숫자로');
  assert.equal(edit.salaryMax, null, '빈칸 = 상한 없음');
  assert.equal(edit.code, undefined, '수정 때는 코드 · 연도를 안 보냄');
  const add = s.toRequestBody(
    s.TARGET.DEDUCTION_TYPE,
    { ...form, salaryMin: '70000000' },
    row,
    true,
  );
  assert.equal(add.id, null);
  assert.equal(add.code, 'CARD_MARKET', '구간 추가는 같은 코드');
  assert.equal(add.categoryId, 3);
  assert.throws(
    () => s.toRequestBody(s.TARGET.DEDUCTION_TYPE, { ...form, rate: '-1' }, row),
    /공제율/,
  );
  assert.throws(
    () => s.toRequestBody(s.TARGET.TAX_BRACKET, { lowerAmount: 'abc', sourceNote: 'x' }, {}),
    /구간 시작/,
  );
  const benefit = s.toRequestBody(
    s.TARGET.BENEFIT_CONDITION,
    s.formFromRow(s.TARGET.BENEFIT_CONDITION, {
      benefitId: 3,
      benefitName: '중소기업 감면',
      homelessHeadReqYn: 'N',
      sourceNote: '§30',
    }),
    { benefitId: 3 },
  );
  assert.equal(benefit.id, 3);
  assert.equal(benefit.companySize, null, '빈 회사 규모 = 조건 없음');

  // ── 필수 칸 · 이력으로 되돌리기 ──
  assert.deepEqual(s.missingRequired(s.TARGET.DEDUCTION_TYPE, { ...form, sourceNote: '  ' }), [
    'sourceNote',
  ]);
  assert.deepEqual(s.missingRequired(s.TARGET.TAX_BRACKET, {}), [
    'lowerAmount',
    'rate',
    'baseAmount',
    'sourceNote',
  ]);
  const back = s.formForRevert(s.TARGET.DEDUCTION_TYPE, row, {
    columnName: 'RATE',
    oldValue: '30',
    newValue: '40',
  });
  assert.equal(back.key, 'rate');
  assert.equal(back.form.rate, '30', '그 칸만 이전 값으로');
  assert.equal(back.form.sourceNote, '조특법', '나머지는 지금 값 그대로');
  assert.equal(
    s.formForRevert(s.TARGET.DEDUCTION_TYPE, row, { columnName: 'SALARY_MAX', oldValue: null }).form
      .salaryMax,
    '',
    '이전에 비어 있었으면 빈칸(= 상한 없음)',
  );
  assert.equal(
    s.formForRevert(s.TARGET.DEDUCTION_TYPE, row, { columnName: 'TYPE_CODE' }),
    null,
    '못 바꾸는 칸은 되돌리기 없음',
  );

  // ── 저장: id 있으면 PUT · 없으면 POST ──
  calls = [];
  await s.saveTaxCriteria(edit, { fetchImpl: fakeFetch(200, null, calls) });
  assert.equal(calls[0].init.method, 'PUT');
  assert.equal(calls[0].url, '/api/admin/tax-criteria');
  calls = [];
  await s.saveTaxCriteria(add, { fetchImpl: fakeFetch(200, null, calls) });
  assert.equal(calls[0].init.method, 'POST');
  assert.throws(() => s.saveTaxCriteria({ ...edit, sourceNote: ' ' }), /근거/);
  assert.throws(() => s.saveTaxCriteria({ ...edit, rate: 101 }), /0~100/);
  await assert.rejects(
    s.saveTaxCriteria(add, {
      fetchImpl: fakeFetch(409, {
        code: 'DUPLICATE_CRITERIA',
        message: '같은 연도 · 코드 · 구간 시작 행이 이미 있어요',
      }),
    }),
    (e) => e.code === 'DUPLICATE_CRITERIA',
  );

  // ── 연도 복사 · 이력 ──
  calls = [];
  await s.copyTaxCriteriaYear('2026', '2027', { fetchImpl: fakeFetch(200, null, calls) });
  assert.equal(calls[0].url, '/api/admin/tax-criteria/years');
  assert.deepEqual(JSON.parse(calls[0].init.body), { sourceYear: 2026, targetYear: 2027 });
  assert.throws(() => s.copyTaxCriteriaYear(2026, 2026), /같아요/);
  calls = [];
  const hist = await s.fetchCriteriaHistory('TAX_BRACKET', 5, {
    fetchImpl: fakeFetch(200, [{ historyId: 1 }], calls),
  });
  assert.equal(calls[0].url, '/api/admin/tax-criteria/history?targetType=TAX_BRACKET&id=5');
  assert.equal(hist.length, 1);
  await assert.rejects(s.fetchCriteriaHistory('MEMBER', 1), /구분/);

  // ── 서버 꺼짐 · 금액 표시 ──
  await assert.rejects(
    s.fetchTaxCriteria(2026, {
      fetchImpl: async () => {
        throw new Error('down');
      },
    }),
    (e) => e.code === 'OFFLINE' && /관리자 서버/.test(e.message),
  );
  assert.equal(s.formatAmount(70000000), '70,000,000');
  assert.equal(s.formatAmount(null, '상한 없음'), '상한 없음');

  console.log(
    'PASS: tax-criteria-service 조회 · 폼↔본문 · 필수 칸 · 되돌리기 · PUT/POST · 409 · 연도 복사 · 이력 · OFFLINE',
  );
} finally {
  fs.rmSync(dir, { recursive: true, force: true });
}
