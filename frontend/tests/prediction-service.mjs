import assert from 'node:assert/strict';
import { build } from 'esbuild';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

// 조건 변경 시뮬레이션 · 결과 이력 API 연결 검사 (서버 없이 가짜 fetch로 확인)
const dir = fs.mkdtempSync(path.join(process.cwd(), 'tests/.prediction-'));
try {
  await build({
    entryPoints: ['src/api/prediction-service.js'],
    outfile: `${dir}/prediction.mjs`,
    platform: 'node',
    format: 'esm',
    bundle: true,
    logLevel: 'silent',
  });
  const {
    cardSpends,
    homelessHeadYn,
    simulationRequest,
    simulate,
    fetchPredictions,
    fetchPrediction,
    DEFAULT_SCENARIO_NAME,
  } = await import(pathToFileURL(`${dir}/prediction.mjs`));

  const envelope = (status, body) =>
    status < 400
      ? { success: true, data: body, meta: null, error: null }
      : {
          success: false,
          data: null,
          meta: null,
          error: { code: body.error, message: body.message },
        };
  const fake =
    (status, body, calls = []) =>
    async (url, init) => {
      calls.push({ url, method: init.method, headers: init.headers, body: init.body });
      return { ok: status < 400, status, json: async () => envelope(status, body) };
    };
  const result = {
    predictionId: 12,
    taxYear: 2025,
    totalSalary: 36000000,
    withheldTax: 1523000,
    taxableIncome: 20050000,
    calculatedTax: 1747500,
    decidedTax: 987500,
    expectedRefund: 535500,
    details: [
      {
        typeCode: 'PENSION_SAVING',
        typeName: '연금저축',
        kind: 'TAX_CREDIT',
        baseAmount: 4000000,
        deductionAmount: 600000,
        sourceNote: '',
      },
    ],
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
    pensionSaving: 4000000,
    irp: 1000000,
    sme: '아니요',
    housing: '무주택',
    headOfHousehold: '예',
    scenarioName: '연금 추가 납입',
    simulationSpend: [
      { category: 'TRANSIT', payment: 'CHECK', amount: 300000 },
      { category: 'GENERAL', payment: 'CREDIT', amount: 0 }, // 0원 줄은 보내지 않음
    ],
  };
  const tx = [
    { date: '2025-03-01', category: '일반', paymentMethod: '신용카드', amount: 100000 },
    { date: '2025-03-02', category: '일반', paymentMethod: '신용카드', amount: 50000 },
    { date: '2025-03-03', category: '대중교통', paymentMethod: '체크카드', amount: 20000 },
    { date: '2025-03-04', category: '공제 대상 아님', paymentMethod: '현금영수증', amount: 7000 },
    { date: '2024-12-31', category: '일반', paymentMethod: '신용카드', amount: 999 }, // 다른 연도
    {
      date: '2025-03-05',
      category: '일반',
      paymentMethod: '신용카드',
      amount: 888,
      duplicateState: 'pending', // 중복 확인 중 → 집계 안 함
    },
    { date: '2025-03-06', category: '일반', paymentMethod: '확인 필요', amount: 777 }, // 결제수단 모름
  ];

  // 무주택 세대주: 무주택 + 예만 Y · 아니요/주택 보유는 N · 확인 필요는 막음
  assert.equal(homelessHeadYn({ housing: '무주택', headOfHousehold: '예' }), 'Y');
  assert.equal(homelessHeadYn({ housing: '무주택', headOfHousehold: '아니요' }), 'N');
  assert.equal(homelessHeadYn({ housing: '주택 보유', headOfHousehold: '예' }), 'N');
  assert.throws(
    () => homelessHeadYn({ housing: '무주택', headOfHousehold: '확인 필요' }),
    (e) => e.code === 'VALIDATION',
  );

  // 카드 사용액: 그 연도 · 집계 포함 거래 + 가상 추가액을 분류 × 결제수단별로 합침
  assert.deepEqual(cardSpends(tx, input.simulationSpend, 2025), [
    { categoryId: 1, payMethod: 'CREDIT', amount: 150000 },
    { categoryId: 2, payMethod: 'CHECK', amount: 320000 },
    { categoryId: 5, payMethod: 'CASH', amount: 7000 },
  ]);
  assert.throws(
    () => cardSpends([], [{ category: 'GENERAL', payment: 'CREDIT', amount: -1 }], 2025),
    /0원 이상/,
  );
  // 취소가 더 많아 음수가 되면 0
  assert.deepEqual(
    cardSpends(
      [{ date: '2025-01-01', category: '일반', paymentMethod: '신용카드', amount: -5000 }],
      [],
      2025,
    ),
    [{ categoryId: 1, payMethod: 'CREDIT', amount: 0 }],
  );

  // 요청 본문: 기준 결과 번호 · 시나리오 이름 · 소득 전부 · 무주택 세대주 · 카드 사용액
  const body = simulationRequest(input, tx, 6);
  assert.equal(body.basePredictionId, 6);
  assert.equal(body.scenarioName, '연금 추가 납입');
  assert.equal(body.income.pensionSaving, 4000000);
  assert.equal(body.income.smeReductionYn, 'N');
  assert.equal(body.homelessHeadYn, 'Y');
  assert.equal(body.cardSpends.length, 3);
  // 이름이 비면 기본 이름 · 16자 넘으면 막음
  assert.equal(
    simulationRequest({ ...input, scenarioName: '  ' }, [], 6).scenarioName,
    DEFAULT_SCENARIO_NAME,
  );
  assert.throws(
    () => simulationRequest({ ...input, scenarioName: '가'.repeat(17) }, [], 6),
    /16자/,
  );
  // 기준 결과가 없으면 요청하지 않음
  assert.throws(
    () => simulationRequest(input, [], null),
    (e) => e.code === 'NO_BASE',
  );

  // 시뮬레이션 요청: POST /api/simulations/2025 · 응답 모양 검사
  const calls = [];
  const sr = await simulate(input, tx, 6, { fetchImpl: fake(200, result, calls), memberId: '1' });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, '/api/simulations/2025');
  assert.equal(calls[0].method, 'POST');
  assert.equal(calls[0].headers['X-Member-Id'], '1');
  assert.equal(JSON.parse(calls[0].body).basePredictionId, 6);
  assert.equal(sr.predictionId, 12);
  assert.equal(sr.expectedRefund, 535500);
  // 입력이 잘못되면 요청을 보내지 않음
  const noCalls = [];
  await assert.rejects(
    simulate({ ...input, sme: '확인 필요' }, tx, 6, { fetchImpl: fake(200, result, noCalls) }),
    (e) => e.code === 'VALIDATION',
  );
  assert.equal(noCalls.length, 0);
  // 기준 결과가 남의 것 · 없음 → 서버 문구 그대로
  await assert.rejects(
    simulate(input, tx, 999, {
      fetchImpl: fake(404, { error: 'NOT_FOUND', message: '기준 결과를 찾을 수 없어요' }),
    }),
    (e) => e.code === 'NOT_FOUND' && e.message === '기준 결과를 찾을 수 없어요',
  );
  // 서버 꺼짐 → OFFLINE
  await assert.rejects(
    simulate(input, tx, 6, {
      fetchImpl: async () => {
        throw new TypeError('Failed to fetch');
      },
    }),
    (e) => e.code === 'OFFLINE',
  );

  // 결과 이력 목록: GET /api/predictions?year=2025
  const listCalls = [];
  const rows = await fetchPredictions(2025, {
    fetchImpl: fake(
      200,
      [
        {
          predictionId: 12,
          taxYear: 2025,
          scenarioName: '연금 추가 납입',
          basePredictionId: 6,
          expectedRefund: 535500,
          predictedAt: '2026-11-16T15:00:00',
        },
        {
          predictionId: 6,
          taxYear: 2025,
          scenarioName: '현재',
          basePredictionId: null,
          expectedRefund: 435500,
          predictedAt: '2026-11-16T14:00:00',
        },
      ],
      listCalls,
    ),
  });
  assert.equal(listCalls[0].url, '/api/predictions?year=2025');
  assert.equal(listCalls[0].method, 'GET');
  assert.equal(rows.length, 2);
  assert.equal(rows[0].basePredictionId, 6);
  assert.equal(rows[1].basePredictionId, null);
  // 목록이 배열이 아니면 오류
  await assert.rejects(fetchPredictions(2025, { fetchImpl: fake(200, {}) }));

  // 하나 열기: GET /api/predictions/12
  const oneCalls = [];
  const one = await fetchPrediction(12, { fetchImpl: fake(200, result, oneCalls) });
  assert.equal(oneCalls[0].url, '/api/predictions/12');
  assert.equal(one.details[0].typeCode, 'PENSION_SAVING');
  await assert.rejects(fetchPrediction(0), (e) => e.code === 'VALIDATION');
  await assert.rejects(
    fetchPrediction(99, {
      fetchImpl: fake(404, { error: 'NOT_FOUND', message: '계산 결과를 찾을 수 없어요' }),
    }),
    (e) => e.code === 'NOT_FOUND',
  );

  console.log('prediction-service: 시뮬레이션 · 결과 이력 API 연결 검사 통과');
} finally {
  fs.rmSync(dir, { recursive: true, force: true });
}
