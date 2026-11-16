import assert from 'node:assert/strict';
import { build } from 'esbuild';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

// 소득 저장 · 세액 계산 API 연결 검사 (서버 없이 가짜 fetch로 확인)
const dir = fs.mkdtempSync(path.join(process.cwd(), 'tests/.calculation-'));
try {
  await build({
    entryPoints: ['src/api/calculation-service.js'],
    outfile: `${dir}/calculation.mjs`,
    platform: 'node',
    format: 'esm',
    bundle: true,
    logLevel: 'silent',
  });
  const { requestCalculation, saveAndCalculate } = await import(
    pathToFileURL(`${dir}/calculation.mjs`)
  );
  await build({
    entryPoints: ['src/api/income-service.js'],
    outfile: `${dir}/income.mjs`,
    platform: 'node',
    format: 'esm',
    bundle: true,
    logLevel: 'silent',
  });
  const { incomeRequest, fetchIncome } = await import(pathToFileURL(`${dir}/income.mjs`));
  await build({
    entryPoints: ['src/api/api-client.js'],
    outfile: `${dir}/client.mjs`,
    platform: 'node',
    format: 'esm',
    bundle: true,
    logLevel: 'silent',
  });
  const { canUseDemo, demoFallbackAllowed } = await import(pathToFileURL(`${dir}/client.mjs`));

  const ok = {
    predictionId: 7,
    taxYear: 2025,
    totalSalary: 36000000,
    withheldTax: 1523000,
    taxableIncome: 20050000,
    calculatedTax: 1747500,
    decidedTax: 1087500,
    expectedRefund: 435500,
    details: [
      {
        typeCode: 'EARNED_DEDUCTION',
        typeName: '근로소득공제',
        kind: 'INCOME_DEDUCTION',
        baseAmount: 36000000,
        deductionAmount: 10650000,
        sourceNote: '국세청 신고안내',
      },
    ],
  };
  // 요청을 순서대로 기록하는 가짜 fetch
  // back 공통 봉투(ApiResponse) 모양으로 감싸요 · 테스트 데이터는 {error, message} / 결과 그대로 적고 여기서 변환
  const envelope = (status, body) =>
    status < 400
      ? { success: true, data: body, meta: null, error: null }
      : {
          success: false,
          data: null,
          meta: null,
          error: {
            code: body.error,
            message: body.message,
            ...(body.errorId ? { errorId: body.errorId } : {}),
          },
        };
  const fakeSeq =
    (responses, calls = []) =>
    async (url, init) => {
      calls.push({ url, method: init.method, headers: init.headers, body: init.body });
      const [status, body] = responses[calls.length - 1];
      return { ok: status < 400, status, json: async () => envelope(status, body) };
    };
  const input = {
    taxYear: 2025,
    salary: 36000000,
    paid: 1523000,
    nationalPension: 1620000,
    healthEmploymentInsurance: 1400000,
    family: 2,
    children: 1,
    rent: 500000,
    housingSaving: 1200000,
    pensionSaving: 3000000,
    irp: 1000000,
    sme: '아니요',
  };

  // 화면 값 → back 이름 (PUT은 모든 칸) · 연금저축 · IRP는 따로 · '확인 필요'는 N
  assert.deepEqual(incomeRequest(input), {
    totalSalary: 36000000,
    withheldTax: 1523000,
    nationalPension: 1620000,
    healthInsurance: 1400000,
    dependents: 2,
    children: 1,
    monthlyRent: 500000,
    housingSaving: 1200000,
    pensionSaving: 3000000,
    irpSaving: 1000000,
    smeReductionYn: 'N',
  });
  assert.equal(incomeRequest({ ...input, sme: '예' }).smeReductionYn, 'Y');
  assert.equal(incomeRequest({ ...input, sme: '미해당' }).smeReductionYn, 'N');
  // '확인 필요'는 N으로 바꾸지 않고 막음 (선택 안내)
  assert.throws(() => incomeRequest({ ...input, sme: '확인 필요' }), /감면 신청 여부/);
  // 필수 금액이 비었거나 음수면 0으로 바꾸지 않고 막음
  assert.throws(() => incomeRequest({ ...input, salary: undefined }), /총급여/);
  assert.throws(() => incomeRequest({ ...input, paid: -1 }), /기납부세액/);
  // 선택 금액이 비면 0 · 소수는 반올림
  const partial = incomeRequest({ salary: 30000000.4, paid: 0, sme: '아니요' });
  assert.equal(partial.totalSalary, 30000000);
  assert.equal(partial.irpSaving, 0);
  assert.equal(partial.dependents, 0);

  // 저장(PUT) → 계산(POST) 순서 · 주소 · 본문
  const calls = [];
  const result = await saveAndCalculate(input, {
    fetchImpl: fakeSeq(
      [
        [200, {}],
        [200, ok],
      ],
      calls,
    ),
    memberId: '1',
  });
  assert.equal(calls.length, 2);
  assert.equal(calls[0].url, '/api/incomes/2025');
  assert.equal(calls[0].method, 'PUT');
  assert.equal(calls[0].headers['Content-Type'], 'application/json');
  assert.equal(JSON.parse(calls[0].body).totalSalary, 36000000);
  assert.equal(calls[1].url, '/api/calculations/2025');
  assert.equal(calls[1].method, 'POST');
  assert.equal(calls[1].body, undefined);
  assert.equal(calls[1].headers['X-Member-Id'], '1');
  assert.equal(result.expectedRefund, 435500);
  assert.equal(result.predictionId, 7); // 저장된 결과 번호를 버리지 않음 (챗봇 · 절세 가이드용)
  assert.equal(result.details[0].typeCode, 'EARNED_DEDUCTION');

  // 저장이 실패하면 계산 요청을 보내지 않음 · 서버 문구 그대로
  const failCalls = [];
  await assert.rejects(
    saveAndCalculate(input, {
      fetchImpl: fakeSeq(
        [[400, { error: 'VALIDATION', message: '총급여를 입력해 주세요' }]],
        failCalls,
      ),
    }),
    (e) => e.code === 'VALIDATION' && e.message === '총급여를 입력해 주세요',
  );
  assert.equal(failCalls.length, 1);

  // 기준값 없는 연도 · 계산 서버 연결 불가는 코드 그대로
  await assert.rejects(
    requestCalculation(2025, {
      fetchImpl: fakeSeq([[400, { error: 'NO_CRITERIA', message: '2025년 기준값이 없어요' }]]),
    }),
    (e) => e.code === 'NO_CRITERIA',
  );
  await assert.rejects(
    requestCalculation(2025, {
      fetchImpl: fakeSeq([
        [
          503,
          {
            error: 'CALC_UNAVAILABLE',
            message: '계산 서버에 잠시 연결할 수 없어요',
            errorId: 'a3f9c2d1',
          },
        ],
      ]),
    }),
    // 서버 오류의 추적 번호는 문구 뒤에 붙어 사용자에게 보여요
    (e) =>
      e.code === 'CALC_UNAVAILABLE' &&
      e.errorId === 'a3f9c2d1' &&
      e.message === '계산 서버에 잠시 연결할 수 없어요 (오류 번호 a3f9c2d1)',
  );
  // 공통 봉투가 아닌 JSON(다른 서버 응답) → OFFLINE
  await assert.rejects(
    requestCalculation(2025, {
      fetchImpl: async () => ({ ok: true, status: 200, json: async () => ({ decidedTax: 1 }) }),
    }),
    (e) => e.code === 'OFFLINE',
  );
  // 서버 꺼짐(네트워크 오류 · JSON 아님) → OFFLINE
  await assert.rejects(
    requestCalculation(2025, {
      fetchImpl: async () => {
        throw new TypeError('Failed to fetch');
      },
    }),
    (e) => e.code === 'OFFLINE',
  );
  await assert.rejects(
    requestCalculation(2025, {
      fetchImpl: async () => ({ ok: false, status: 502, json: async () => JSON.parse('<html>') }),
    }),
    (e) => e.code === 'OFFLINE',
  );
  // 체험용 계산으로 대신하는 건 「연결 실패 + 개발 서버」일 때만 (배포본은 안내만)
  assert.equal(canUseDemo({ code: 'OFFLINE' }, true), true);
  assert.equal(canUseDemo({ code: 'OFFLINE' }, false), false);
  assert.equal(canUseDemo({ code: 'CALC_UNAVAILABLE' }, true), false);
  assert.equal(canUseDemo(undefined, true), false);
  // 테스트(node)에는 개발 서버 표시(DEV)가 없어요 → 기본값은 허용 안 함
  assert.equal(demoFallbackAllowed, false);
  assert.equal(canUseDemo({ code: 'OFFLINE' }), false);
  // 응답에 숫자가 빠지면 임의로 채우지 않고 오류
  await assert.rejects(
    requestCalculation(2025, { fetchImpl: fakeSeq([[200, { ...ok, decidedTax: null }]]) }),
    /decidedTax/,
  );
  // 잘못된 연도 · 숫자 아닌 회원번호
  await assert.rejects(requestCalculation(1999), (e) => e.code === 'VALIDATION');
  const seenBad = [];
  await requestCalculation(2025, { fetchImpl: fakeSeq([[200, ok]], seenBad), memberId: '1; DROP' });
  assert.equal(seenBad[0].headers['X-Member-Id'], undefined);
  // 소득 조회 (GET)
  const getCalls = [];
  await fetchIncome(2025, { fetchImpl: fakeSeq([[200, { taxYear: 2025 }]], getCalls) });
  assert.equal(getCalls[0].method, 'GET');
  assert.equal(getCalls[0].url, '/api/incomes/2025');

  console.log(
    'PASS: 소득 저장 · 세액 계산 API 연결 · 저장 후 계산 순서 · 오류 코드 · 연결 실패 · 응답 검사',
  );
} finally {
  fs.rmSync(dir, { recursive: true, force: true });
}
