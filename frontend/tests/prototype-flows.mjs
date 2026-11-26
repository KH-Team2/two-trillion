import { build } from 'esbuild';
import { pathToFileURL } from 'node:url';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';
const dir = fs.mkdtempSync(path.join(process.cwd(), 'tests/.hope-flow-'));

// 운영 소스의 JavaScript 모듈을 임시로 묶어 기존 서비스 회귀 검증을 실행합니다.
// [수정 11/23] 체험판 기능 삭제에 맞춰 정리했어요 · 이유: 테스트하던 코드가 삭제됨
//   삭제한 검사: 브라우저 체험 계정(createAccountService · 빠른 체험 · 관리자 회원 관리 · 탈퇴),
//   브라우저 파일 해석(parseUploadCSV · parseUploadFile · 예시 상호명 분류), 체험용 계산(taxService),
//   브라우저 문의 · 기준값 · 접근 기록(prototype-records)
//   남긴 검사: 나이 계산 · 업로드 묶음 · 중복 · 집계 · 입력 검사 · 표기 · 그룹 통계 · ERD DTO
const sourceModules = {
  'account-service': 'src/api/account-service.js',
  services: 'src/api/services.js',
  'expense-service': 'src/api/expense-service.js',
  'dashboard-model': 'src/utils/dashboard-model.js',
  'group-model': 'src/utils/group-model.js',
  'erd-contract': 'src/api/erd-contract.js',
};
for (const [name, entry] of Object.entries(sourceModules)) {
  await build({
    entryPoints: [entry],
    outfile: path.join(dir, `${name}.mjs`),
    bundle: true,
    platform: 'node',
    format: 'esm',
    packages: 'external',
    logLevel: 'silent',
  });
}
const { ageOn } = await import(pathToFileURL(path.join(dir, 'account-service.mjs')).href);
const { uploadTotals, transactionExamples, flagDuplicates, removeUpload, makeMeta, uploadBlocker } =
  await import(pathToFileURL(path.join(dir, 'expense-service.mjs')).href);
const {
  initial,
  normalizeTaxData,
  validateTaxData,
  settlementLabel,
  settlementTone,
  settlementText,
} = await import(pathToFileURL(path.join(dir, 'services.mjs')).href);
const { dashboardModel } = await import(pathToFileURL(path.join(dir, 'dashboard-model.mjs')).href);
const { groupComparison } = await import(pathToFileURL(path.join(dir, 'group-model.mjs')).href);
const dto = await import(pathToFileURL(path.join(dir, 'erd-contract.mjs')).href);

// 만 나이(한국 시각 기준)
const now = Date.parse('2026-10-28T15:00:00Z');
assert.equal(ageOn('2012-10-29', new Date(now)), 14);
for (const birth of ['2027-01-01', '2001-02-29', '2026-02-30', 'abc'])
  assert.throws(() => ageOn(birth, new Date(now)));

// 서버 업로드 결과와 같은 모양의 거래 (서버가 분류 · 결제수단을 채워 보냄)
const tx = (id, date, merchant, amount, category, paymentMethod, uploadId) => ({
  id,
  date,
  merchant,
  amount,
  category,
  paymentMethod,
  source: 'upload',
  uploadId,
});
const a = [
    tx(1, '2026-10-01', '서울교통공사', 65000, '대중교통', '체크카드', 'a'),
    tx(2, '2026-10-02', '우리상점', 20000, '일반', '신용카드', 'a'),
  ],
  b = [tx(3, '2026-10-03', '교보문고', 30000, '문화체육', '현금영수증', 'b')];
assert.deepEqual(uploadTotals([...a, ...b]), { credit: 20000, debit: 65000, cash: 30000 });
const unknown = [tx(4, '2026-10-04', '미등록상호', 1000, '일반', '확인 필요', 'c')];
assert.deepEqual(uploadTotals(unknown), { credit: 0, debit: 0, cash: 0 });
assert.match(uploadBlocker(unknown), /결제수단/);
assert.equal(uploadBlocker(a), '');
const dup = flagDuplicates(
  a,
  a.map((r) => ({ ...r, id: r.id + 100, uploadId: 'dup' })),
);
assert(dup.every((r) => r.duplicateState === 'pending'));
assert.deepEqual(uploadTotals([...a, ...dup]), uploadTotals(a));
// 대시보드 모델은 서버 계산 결과를 받아요 (체험용 계산 기본값 없음)
const serverResult = { taxYear: 2026, decidedTax: 1100000, expectedRefund: 423000, details: [] };
assert.equal(dashboardModel(initial, [...a, ...dup], serverResult).total, 85000);
assert.equal(dashboardModel(initial, [...a, ...dup], serverResult).held, 2);
const files = [
  { id: 'a', name: 'a.csv', count: 2, at: '2026-10-29', source: 'card', status: '업로드 완료' },
  { id: 'b', name: 'b.csv', count: 1, at: '2026-10-29', source: 'hometax', status: '업로드 완료' },
];
const deleted = removeUpload([...a, ...b], files, 'a');
assert.deepEqual(deleted.rows, b);
assert.equal(deleted.meta.files[0].id, 'b');
assert.equal(makeMeta([]), null);
const many = Array.from({ length: 20 }, (_, i) => ({ ...a[1], id: i }));
const selected = transactionExamples(many, '일반');
assert.equal(selected.count, 20);
assert.equal(selected.examples.length, 15);
assert.equal(selected.total, 400000);
assert.equal(transactionExamples(a, '전통시장').count, 0);
assert.equal(transactionExamples(many, '일반', 30).examples.length, 20);

// 입력값 정리 · 검사
const legacy = { ...initial, rent: 12000000 };
delete legacy.rentPeriod;
const converted = normalizeTaxData(legacy);
assert.equal(converted.rent, 0);
assert.equal(converted.legacyAnnualRent, 12000000);
assert.equal(converted.legacyRentNeedsReview, true);
assert.match(validateTaxData({ ...initial, family: 1, children: 2 }), /자녀/);
assert.equal(validateTaxData({ ...initial, family: 2, children: 2 }), '');

// 환급 · 추가 납부 표기 (색 클래스 + 글자 표기)
assert.equal(settlementLabel(-300000), '예상 추가 납부액');
assert.equal(settlementLabel(0), '환급 및 추가 납부 예상액');
assert.equal(settlementLabel(1), '예상 환급액');
assert.equal(settlementTone(446240), 'amount-refund');
assert.equal(settlementTone(-12000), 'amount-pay');
assert.equal(settlementTone(0), 'amount-zero');
assert.equal(settlementText(446240), '환급 446,240원');
assert.equal(settlementText(-12000), '추가 납부 12,000원');
assert.equal(settlementText(0), '0원');

// 그룹 통계 (성별 · 연령 / 총급여 독립 비교 · 나의 값은 서버 계산 결과)
const groups = groupComparison({ ...initial, gender: '여성', salary: 30000000 }, serverResult);
assert.equal(groups.rows.length, 3);
assert.match(groups.rows[0].label, /30세 미만 여성/);
assert.match(groups.rows[2].label, /2,000만 원 초과~3,000만 원 이하/);
assert.equal(groups.rows[1].decidedTax, 1100000);
assert.equal(
  groupComparison({ ...initial, gender: '남성', salary: 30000000 }, serverResult).rows[2]
    .decidedTax,
  groups.rows[2].decidedTax,
);
assert.equal(
  groupComparison({ ...initial, salary: 60000000 }, serverResult).rows[0].decidedTax,
  groupComparison(initial, serverResult).rows[0].decidedTax,
);
const s1499 = groupComparison({ ...initial, salary: 15000000 }, serverResult),
  s1501 = groupComparison({ ...initial, salary: 15000001 }, serverResult);
assert.equal(s1499.salaryStat.SALARY_MAX, 15000000);
assert.equal(s1501.salaryStat.SALARY_MIN, 15000000);
assert.equal(
  groupComparison({ ...initial, birth: '1956-12-31' }, serverResult).ageStat.AGE_MIN,
  70,
);
assert.equal(
  groupComparison({ ...initial, birth: '1966-12-31' }, serverResult).ageStat.AGE_MAX,
  69,
);
assert.equal(groupComparison({ ...initial, gender: '' }, serverResult).rows[0].decidedTax, null);
// 서버 조회 결과(GET /api/group-stats)를 넘기면 화면 입력값 대신 서버가 찾은 그룹을 써요
assert.equal(groupComparison(initial, serverResult).fromServer, false);
const serverGroups = groupComparison(
  { ...initial, gender: '남성', salary: 30000000 },
  serverResult,
  {
    taxYear: 2026,
    ageGroup: {
      statYear: 2024,
      groupLabel: '여성 40대',
      peopleCount: 2037259,
      avgSalary: 41558266,
      avgDecidedTax: 2274940,
      avgRefundTax: 577207,
    },
    salaryGroup: null,
  },
);
assert.equal(serverGroups.fromServer, true);
assert.equal(serverGroups.statYear, 2024);
assert.equal(serverGroups.rows[0].label, '여성 40대 평균');
assert.equal(serverGroups.rows[0].decidedTax, 2274940);
assert.equal(serverGroups.rows[0].expectedRefund, 577207);
assert.equal(serverGroups.ageGroup, '40대');
// 서버가 못 찾은 그룹(null)은 화면 입력값으로 대신 찾지 않아요
assert.equal(serverGroups.salaryGroup, '총급여 구간 없음');
assert.equal(serverGroups.rows[2].decidedTax, null);
assert.match(groupComparison(initial, serverResult).rows[1].kind, /계산 결과/);
const official = JSON.parse(fs.readFileSync('src/data/statistics/group-stat-2024.json'));
assert.equal(official.length, 28);
assert.equal(official.filter((r) => r.GROUP_TYPE === 'GENDER_AGE').length, 12);
const statMetadata = JSON.parse(fs.readFileSync('src/data/statistics/group-stat-metadata.json'));
for (const row of official) {
  assert.equal(
    row.AVG_REFUND_TAX,
    Math.round((statMetadata[row.STAT_ID].REFUND_TOTAL_MIL * 1e6) / row.PEOPLE_COUNT),
  );
  assert.equal(
    row.AVG_DECIDED_TAX,
    Math.round((statMetadata[row.STAT_ID].DECIDED_TOTAL_MIL * 1e6) / row.PEOPLE_COUNT),
  );
}
assert.equal(groups.rows[0].decidedTax, 510931);
assert.equal(groups.rows[2].decidedTax, 135390);

// ERD DTO
assert.deepEqual(dto.memberProfileDTO('여성'), { GENDER: 'F' });
assert.equal(dto.incomeDTO({ ...initial, pensionSaving: 100, irp: 200 }).PENSION_SAVING, 300);
assert.equal(dto.qualificationDTO({ ...initial, companySize: '중소기업' }).COMPANY_SIZE, 'SME');
assert.equal(
  dto.expenseDTO(
    { ...tx(5, '2026-10-01', '검증상점', 100, '일반', '체크카드', 10), CATEGORY_CODE: 'GENERAL' },
    { GENERAL: 123 },
  ).PAY_METHOD,
  'CHECK',
);
console.log(
  'PASS: age KST, upload groups/duplicates/totals/deletion/payment blocker, classification examples 15 limit, legacy monthly rent re-entry, family validation, zero/additional payment labels, independent 3-bar groups with server result, official 28-group source averages/boundaries, ERD DTO.',
);
fs.rmSync(dir, { recursive: true, force: true });
