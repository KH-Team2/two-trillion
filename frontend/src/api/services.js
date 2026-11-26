import { getDefaultTaxYear, getTaxYear } from '../utils/tax-year.js';
// 입력 기본값 · 입력 검사 · 화면 표기 · 브라우저 입력 저장(userService)입니다.
// [수정 11/23] 체험용 코드 삭제 · 이유: 체험판 기능 삭제 결정
//   sampleTransactions(예시 거래) · taxService(체험용 계산) · merchantService · parseCSV(예시 상호명 대조)
//   · aiService(미리 써 둔 AI 답변) · annualRent(체험용 계산에서만 씀)
export const initial = {
  taxYear: getDefaultTaxYear(),
  nationalPension: 0,
  healthEmploymentInsurance: 0,
  housingSaving: 0,
  pensionSaving: 0,
  irp: 0,
  name: '사용자',
  birth: '1999-05-20',
  gender: '여성',
  email: 'hope@example.com',
  salary: 36000000,
  paid: 1523000,
  credit: 0,
  debit: 0,
  cash: 0,
  family: 0,
  children: 0,
  militaryMonths: 0,
  companySize: '확인 필요',
  headOfHousehold: '확인 필요',
  rentPeriod: 'monthly',
  rent: 0,
  housing: '무주택',
  sme: '확인 필요',
  employed: '2023-03-01',
  consent: false,
};
export function normalizeTaxData(d) {
  return {
    ...d,
    taxYear: getTaxYear(d),
    nationalPension: d.nationalPension ?? 0,
    healthEmploymentInsurance: d.healthEmploymentInsurance ?? 0,
    // 과거 통합 보험료를 임의로 배분하지 않습니다. 원본 값은 보존하고 재입력 안내만 합니다.
    legacyInsuranceNeedsReview:
      d.legacyInsuranceNeedsReview || (d.socialInsurance > 0 && d.nationalPension == null),
    housingSaving: d.housingSaving ?? 0,
    pensionSaving: d.pensionSaving ?? 0,
    irp: d.irp ?? 0,
    children: d.children ?? 0,
    militaryMonths: d.militaryMonths ?? 0,
    companySize: d.companySize ?? '확인 필요',
    headOfHousehold: d.headOfHousehold ?? '확인 필요',
    rent: d.rentPeriod === 'monthly' ? d.rent : 0,
    legacyAnnualRent: d.legacyAnnualRent ?? (d.rentPeriod !== 'monthly' ? d.rent : null),
    legacyRentNeedsReview: d.legacyRentNeedsReview || (d.rentPeriod !== 'monthly' && d.rent > 0),
    rentPeriod: 'monthly',
  };
}
export function validateTaxData(d) {
  if (!Number.isInteger(d.taxYear) || d.taxYear < 1900 || d.taxYear > 9999)
    return '귀속 연도를 확인해주세요.';
  if (!['확인 필요', '예', '아니요'].includes(d.headOfHousehold))
    return '무주택 세대주 여부를 확인해주세요.';
  if (!['확인 필요', '예', '아니요', '해당', '미해당'].includes(d.sme))
    return '감면 신청 여부를 확인해주세요.';
  if (
    !Number.isInteger(d.family) ||
    d.family < 0 ||
    d.family > 20 ||
    !Number.isInteger(d.children ?? 0) ||
    (d.children ?? 0) < 0 ||
    (d.children ?? 0) > d.family
  )
    return '20세 이하 자녀 수는 추가 부양가족 수(본인 제외) 이하여야 합니다.';
  if (
    !Number.isInteger(d.militaryMonths ?? 0) ||
    (d.militaryMonths ?? 0) < 0 ||
    (d.militaryMonths ?? 0) > 120
  )
    return '군 복무 개월 수는 0~120 사이의 정수로 입력해주세요.';
  if (
    [
      d.salary,
      d.paid,
      d.credit,
      d.debit,
      d.cash,
      d.rent,
      d.nationalPension ?? 0,
      d.healthEmploymentInsurance ?? 0,
      d.housingSaving ?? 0,
      d.pensionSaving ?? 0,
      d.irp ?? 0,
      ...(d.simulationSpend || []).map((r) => r.amount),
    ].some((v) => !Number.isFinite(v) || v < 0)
  )
    return '금액은 0 이상으로 입력해주세요.';
  if (!['중소기업', '중견기업', '대기업', '확인 필요'].includes(d.companySize ?? '확인 필요'))
    return '회사 규모를 선택해주세요.';
  return '';
}
export function settlementLabel(refund) {
  return refund > 0 ? '예상 환급액' : refund < 0 ? '예상 추가 납부액' : '환급 및 추가 납부 예상액';
}
// 환급(돌려받음) · 추가 납부(더 냄)를 색으로도 구분하는 CSS 클래스 (글자 표기와 함께 씀 · 색만으로 구분하지 않음)
export function settlementTone(refund) {
  return refund > 0 ? 'amount-refund' : refund < 0 ? 'amount-pay' : 'amount-zero';
}
// 표 한 칸처럼 좁은 곳에 쓰는 표기: 「환급 446,240원」 · 「추가 납부 12,000원」 · 「0원」
export function settlementText(refund) {
  const amount = `${Math.abs(refund).toLocaleString('ko-KR')}원`;
  return refund > 0 ? `환급 ${amount}` : refund < 0 ? `추가 납부 ${amount}` : amount;
}
export const categories = ['일반', '대중교통', '전통시장', '문화체육', '공제 대상 아님'];
export const categoryCodes = {
  일반: 'GENERAL',
  대중교통: 'TRANSIT',
  전통시장: 'MARKET',
  문화체육: 'CULTURE',
  '공제 제외': 'EXCLUDED',
  '공제 대상 아님': 'EXCLUDED',
};
const scopedKey = (key) =>
  key.startsWith('admin-')
    ? 'hope-' + key
    : 'hope-user-' +
      (typeof window !== 'undefined'
        ? sessionStorage.getItem('hope-session-v1') || 'guest'
        : 'guest') +
      '-' +
      key;
export const userService = {
  load: (key, fallback) => {
    try {
      return JSON.parse(localStorage.getItem(scopedKey(key)) || 'null') ?? fallback;
    } catch {
      return fallback;
    }
  },
  save: (key, value) => localStorage.setItem(scopedKey(key), JSON.stringify(value)),
};
