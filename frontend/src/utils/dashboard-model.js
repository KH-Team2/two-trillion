import { getTaxYear, getDefaultTaxYear } from './tax-year.js';
// 화면용 거래 합계와 자격 안내 상태를 구성합니다. 실제 법정 자격 확정 로직은 아직 연결되지 않았습니다.
import { groupComparison } from './group-model.js';
import { isCounted } from '../api/expense-service.js';
import { categories } from '../api/services.js';
export const TAX_YEAR = getDefaultTaxYear();
export const TAX_GUIDANCE =
  'https://www.nts.go.kr/nts/cm/cntnts/cntntsView.do?cntntsId=7794&mi=2458';
export function yearOf(date) {
  // [수정 11/17] 날짜가 없으면(서버 로그인 회원의 생년월일) null · 전에는 .trim 오류로 대시보드가 빈 화면
  const match = /^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})$/.exec(String(date ?? '').trim());
  if (!match) return null;
  const [, y, m, d] = match.map(Number),
    test = new Date(Date.UTC(y, m - 1, d));
  return test.getUTCFullYear() === y && test.getUTCMonth() === m - 1 && test.getUTCDate() === d
    ? y
    : null;
}
export function youthStatus(data) {
  return ['미해당', '아니요'].includes(data.sme)
    ? {
        status: '비해당',
        // [수정 11/19 TT-124] 「실제 자격 심사는 연결되지 않았습니다」 삭제 (서버 혜택 진단 F-03-02 · 계산도 진단 결과로 감면 적용)
        reason: '감면 신청을 「아니요」로 입력해 감면 없이 계산합니다.',
      }
    : {
        status: '확인 필요',
        reason: ['해당', '예'].includes(data.sme)
          ? '자가 입력은 해당이지만 취업 당시 연령·병역·기업 업종/규모·감면기간 및 신청 증빙이 확인되지 않았습니다.'
          : '감면 자격과 증빙이 확인되지 않았습니다.',
      };
}
// result: 서버 계산 결과 (꼭 넘겨야 해요)
// groupStats: 서버가 찾은 내 그룹(fetchGroupStats 결과) · 없으면(null) 통계 스냅샷에서 찾아요
// [수정 11/23] 체험용 계산 기본값(taxService.calculate) 삭제 · 이유: 체험판 기능 삭제 결정
export function dashboardModel(data, transactions, result, groupStats = null) {
  const groups = groupComparison(data, result, groupStats);
  const taxYear = getTaxYear(data);
  const allRows = transactions.filter((t) => yearOf(t.date) === taxYear);
  const rows = allRows.filter(isCounted);
  const dates = rows
    .map((t) => t.date)
    .sort((a, b) => new Date(a).getTime() - new Date(b).getTime());
  const total = rows.reduce((a, t) => a + t.amount, 0);
  // 동일 연도의 업로드 거래에서 소비 유형을 집계합니다. 계산 응답 details에서 추정하지 않습니다.
  const categoryOf = (row) => (row.category === '공제 제외' ? '공제 대상 아님' : row.category);
  const types = categories.map((category) => {
    const categoryRows = rows.filter((row) => categoryOf(row) === category);
    const heldCount = allRows.filter(
      (row) => categoryOf(row) === category && !isCounted(row),
    ).length;
    const matchedCount = categoryRows.filter((row) =>
      /대조.*일치|상호명 일치/.test(row.method),
    ).length;
    return {
      category,
      spent: categoryRows.reduce((sum, row) => sum + row.amount, 0),
      count: categoryRows.length,
      heldCount,
      matchedCount,
      unmatchedCount: categoryRows.length - matchedCount,
      beforeLimit: null,
      final: null,
    };
  });
  const matched = rows.filter((t) => /대조.*일치|상호명 일치/.test(t.method)).length;
  const ml = rows.filter((t) => /ML|머신러닝|모델 예측/i.test(t.method)).length;
  const user = rows.filter((t) => /사용자 수정|사용자 확인/.test(t.method)).length;
  const other = rows.length - matched - ml - user;
  const age = yearOf(data.birth) !== null ? taxYear - Number(data.birth.slice(0, 4)) : null;
  return {
    result,
    taxYear,
    rows,
    allRows,
    held: allRows.length - rows.length,
    types,
    total,
    excluded: transactions.length - allRows.length,
    period: dates.length ? `${dates[0]} ~ ${dates[dates.length - 1]}` : '거래 없음',
    annualInput: data.credit + data.debit + data.cash,
    minimum: data.salary * 0.25,
    matched,
    ml,
    user,
    other,
    needsReview: rows.length,
    youth: youthStatus(data),
    ageGroup: groups.ageGroup,
    salaryGroup: groups.salaryGroup,
    statYear: groups.statYear,
    groupFromServer: groups.fromServer,
    cultureSalary:
      data.salary <= 70000000
        ? '급여 요건 충족 · 거래 적격은 확인 필요'
        : '급여 요건 미충족 · 문화체육 우대 공제율 미적용',
  };
}
