import { getTaxYear } from './tax-year.js';
import metadata from '../data/statistics/group-stat-metadata.json';
// 독립된 두 그룹(성별·연령 / 총급여)을 조회합니다.
// 서버 조회 결과(GET /api/group-stats/{taxYear})를 넘기면 그 값을 쓰고, 없으면 아래 2024년 통계 스냅샷에서 찾습니다.
import { taxService } from '../api/services.js';
import stats from '../data/statistics/group-stat-2024.json';

// 서버 응답의 그룹 한 줄을 이 파일이 쓰는 칸 이름(GROUP_STAT 열 이름)으로 바꿔요 · 그룹이 없으면(null) undefined
function serverRow(group) {
  if (!group) return undefined;
  return {
    TAX_YEAR: group.statYear,
    GROUP_LABEL: group.groupLabel,
    PEOPLE_COUNT: group.peopleCount,
    AVG_SALARY: group.avgSalary,
    AVG_DECIDED_TAX: group.avgDecidedTax,
    AVG_REFUND_TAX: group.avgRefundTax,
  };
}

// server: fetchGroupStats() 결과 { taxYear, ageGroup, salaryGroup } · 안 넘기면(null) 스냅샷에서 찾아요
// demo: 「나의 계산 결과」가 체험용 계산이면 true, 서버 계산 결과면 false (막대 아래 설명 문구만 달라져요)
export function groupComparison(d, result = taxService.calculate(d), server = null, demo = true) {
  const taxYear = getTaxYear(d);
  const age = taxYear - Number(d.birth.slice(0, 4));
  const gender = d.gender === '여성' ? 'F' : d.gender === '남성' ? 'M' : null;
  const localAge = stats.find(
    (r) =>
      r.GROUP_TYPE === 'GENDER_AGE' &&
      r.GENDER === gender &&
      r.AGE_MIN !== null &&
      age >= r.AGE_MIN &&
      (r.AGE_MAX === null || age <= r.AGE_MAX),
  );
  const localSalary = stats.find(
    (r) =>
      r.GROUP_TYPE === 'SALARY' &&
      r.SALARY_MIN !== null &&
      d.salary > r.SALARY_MIN &&
      (r.SALARY_MAX === null || d.salary <= r.SALARY_MAX),
  );
  // 서버가 찾은 그룹이 있으면 그걸 써요 (서버는 회원 성별 · 생년월일 · 저장된 총급여로 찾음)
  const a = server ? serverRow(server.ageGroup) : localAge;
  const s = server ? serverRow(server.salaryGroup) : localSalary;
  const valid = !!a;
  const statYear = a?.TAX_YEAR ?? s?.TAX_YEAR ?? 2024;
  const myKind = demo ? '데모 계산' : '계산 결과';
  const r = result,
    ageGroup = a?.GROUP_LABEL.replace(/^(남성|여성) /, '') || '성별·연령 정보 확인 필요',
    salaryGroup = s?.GROUP_LABEL || '총급여 구간 없음';
  return {
    taxYear,
    fromServer: !!server,
    statYear,
    salaryPopulation: metadata[s?.STAT_ID]?.POPULATION || '조회 대상 없음',
    ageGroup,
    salaryGroup,
    valid,
    ageStat: a,
    salaryStat: s,
    rows: [
      {
        label: !a
          ? '성별·연령 정보 확인 필요'
          : server
            ? `${a.GROUP_LABEL} 평균`
            : `${ageGroup} ${d.gender} 평균`,
        decidedTax: a?.AVG_DECIDED_TAX ?? null,
        expectedRefund: a?.AVG_REFUND_TAX ?? null,
        kind: `${statYear} 국세통계 · 성별·연령 집단`,
      },
      {
        label: '나의 계산 결과',
        decidedTax: r.decidedTax,
        expectedRefund: r.expectedRefund,
        kind:
          r.expectedRefund < 0
            ? `${taxYear}년 ${demo ? '데모' : '계산 결과'} · 추가 납부 ${Math.abs(r.expectedRefund).toLocaleString()}원`
            : `${taxYear}년 ${myKind}`,
      },
      {
        label: `${salaryGroup} 집단 평균`,
        decidedTax: s?.AVG_DECIDED_TAX ?? null,
        expectedRefund: s?.AVG_REFUND_TAX ?? null,
        kind: `${statYear} 국세통계 · 총급여 집단`,
      },
    ],
  };
}
