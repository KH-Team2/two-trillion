// 관리자 세법 기준값 관리 API 연결 (F-19 · F-08-01 · S-08) · [신규 11/26 고은]
// - GET  /api/admin/tax-criteria?taxYear=           : 연도별 공제 항목 · 세율/공제 구간 · 혜택 조건 (+ 기준값이 있는 연도 목록)
// - PUT  /api/admin/tax-criteria                    : 한 행의 값 고치기 (본문 targetType + id)
// - POST /api/admin/tax-criteria                    : 같은 코드로 행 하나 추가 (구간 추가)
// - POST /api/admin/tax-criteria/years              : 새 연도 추가 (있는 연도를 통째로 복사)
// - GET  /api/admin/tax-criteria/history?targetType=&id= : 한 행의 변경 이력 (DB 트리거가 남김 · 최신순 100줄)
//   관리자(ROLE=ADMIN) 토큰만 통과 · 고친 값은 계산 서버가 5분 동안 기억해서 최대 5분 뒤부터 계산에 반영
import { apiError, apiRequest } from './api-client.js';

const BASE = '/api/admin/tax-criteria';
const OFFLINE_MESSAGE = '관리자 서버에 연결할 수 없어요.';

// 세 테이블 구분 (서버 targetType 값 = DB 테이블 이름)
export const TARGET = {
  DEDUCTION_TYPE: 'DEDUCTION_TYPE',
  TAX_BRACKET: 'TAX_BRACKET',
  BENEFIT_CONDITION: 'BENEFIT_CONDITION',
};
export const TARGET_LABEL = {
  DEDUCTION_TYPE: '공제 항목',
  TAX_BRACKET: '세율 · 공제 구간',
  BENEFIT_CONDITION: '혜택 조건',
};

// DB 값 → 화면 이름 (CK_DEDUCTION_KIND · CK_TAX_BRACKET_TYPE · CK_DEDUCTION_PAY)
export const KIND_LABEL = {
  CARD: '카드 공제율',
  CARD_RULE: '카드 공제 규칙',
  INCOME_DEDUCTION: '소득공제',
  TAX_CREDIT: '세액공제',
  TAX_REDUCTION: '세액감면',
};
export const BRACKET_LABEL = {
  TAX_RATE: '기본세율',
  EARNED_DEDUCTION: '근로소득공제',
  EARNED_CREDIT: '근로소득세액공제',
};
export const PAY_LABEL = { CREDIT: '신용', CHECK: '체크', CASH: '현금영수증' };

// 화면 안내 (원래 서버 응답에 있던 문구를 화면으로 옮김)
export const CRITERIA_NOTICES = [
  '공제율 0%인 행은 정상이에요. 한도 금액만 쓰는 항목이거나, 공제율 · 구간을 「세율 · 공제 구간」에서 읽는 항목이에요.',
  '구간 계산: 누적액 + (금액 − 구간 시작) × 비율 ÷ 100 (원 미만 버림)',
  '근로소득세액공제 한도(74만 · 66만 · 50만 · 20만)는 계산 코드에 있어서 이 화면으로 바꿀 수 없어요.',
  '고친 값은 계산 서버가 5분 동안 기억해서, 최대 5분 뒤부터 계산에 반영돼요.',
];

const YEAR_MIN = 2000;
const YEAR_MAX = 2100;
const isYear = (v) => Number.isInteger(Number(v)) && Number(v) >= YEAR_MIN && Number(v) <= YEAR_MAX;

/** 연도별 기준값 · year를 비우면 서버가 가장 최근 연도를 골라요 */
export async function fetchTaxCriteria(year, options = {}) {
  if (year !== undefined && year !== null && year !== '' && !isYear(year)) {
    throw apiError('VALIDATION', '연도를 확인해 주세요');
  }
  const query = year ? `?taxYear=${encodeURIComponent(year)}` : '';
  const data = await apiRequest(`${BASE}${query}`, { ...options, offlineMessage: OFFLINE_MESSAGE });
  return {
    taxYear: data?.taxYear ?? null,
    years: Array.isArray(data?.years) ? data.years : [],
    deductionTypes: Array.isArray(data?.deductionTypes) ? data.deductionTypes : [],
    taxBrackets: Array.isArray(data?.taxBrackets) ? data.taxBrackets : [],
    benefitConditions: Array.isArray(data?.benefitConditions) ? data.benefitConditions : [],
  };
}

/**
 * 화면 입력칸(글자) → 서버로 보낼 값
 * - 빈칸은 null (= 비움 · 상한 없음 · 조건 없음)
 * - 금액 · 나이처럼 숫자칸은 쉼표를 지우고 숫자로 · 숫자가 아니면 화면에서 먼저 막아요
 */
export function toNumberOrNull(value, label) {
  const text = String(value ?? '')
    .replaceAll(',', '')
    .trim();
  if (text === '') return null;
  const n = Number(text);
  if (!Number.isFinite(n) || n < 0)
    throw apiError('VALIDATION', `${label}을(를) 0 이상 숫자로 입력해 주세요`);
  return n;
}

// 표의 한 행 → 수정 칸에 넣을 글자 (숫자는 글자로 · 비어 있으면 빈칸)
const text = (v) => (v === null || v === undefined ? '' : String(v));

/** 표의 한 행 → 수정 폼 값 */
export function formFromRow(targetType, row = {}) {
  if (targetType === TARGET.DEDUCTION_TYPE) {
    return {
      name: text(row.typeName),
      rate: text(row.rate),
      limitAmount: text(row.limitAmount),
      salaryMin: text(row.salaryMin ?? 0),
      salaryMax: text(row.salaryMax),
      sourceNote: text(row.sourceNote),
    };
  }
  if (targetType === TARGET.TAX_BRACKET) {
    return {
      lowerAmount: text(row.lowerAmount),
      upperAmount: text(row.upperAmount),
      rate: text(row.rate),
      baseAmount: text(row.baseAmount ?? 0),
      sourceNote: text(row.sourceNote),
    };
  }
  return {
    name: text(row.benefitName),
    minAge: text(row.minAge),
    maxAge: text(row.maxAge),
    maxMilitaryMonths: text(row.maxMilitaryMonths),
    companySize: text(row.companySize),
    salaryLimit: text(row.salaryLimit),
    homelessHeadReqYn: row.homelessHeadReqYn || 'N',
    rate: text(row.reductionRate),
    limitAmount: text(row.limitAmount),
    periodYears: text(row.periodYears),
    sourceNote: text(row.sourceNote),
  };
}

/**
 * 수정 폼 값 → 서버 요청 본문
 * - isAdd(행 추가)면 id 없이, 바탕이 된 행의 연도 · 코드 · 종류 · 분류 · 결제수단을 그대로 실어 보내요 (같은 코드로 구간 추가)
 * - 숫자칸이 숫자가 아니면 여기서 오류 (서버까지 안 감)
 */
export function toRequestBody(targetType, form, row, isAdd = false) {
  const num = toNumberOrNull;
  const common = {
    targetType,
    id: isAdd ? null : idOf(targetType, row),
    sourceNote: form.sourceNote?.trim() ?? '',
  };
  if (targetType === TARGET.DEDUCTION_TYPE) {
    return {
      ...common,
      name: form.name?.trim() ?? '',
      rate: num(form.rate, '공제율'),
      limitAmount: num(form.limitAmount, '한도'),
      salaryMin: num(form.salaryMin, '총급여 구간 시작') ?? 0,
      salaryMax: num(form.salaryMax, '총급여 구간 끝'),
      ...(isAdd && {
        taxYear: row.taxYear,
        code: row.typeCode,
        deductionKind: row.deductionKind,
        categoryId: row.categoryId ?? null,
        payMethod: row.payMethod ?? null,
      }),
    };
  }
  if (targetType === TARGET.TAX_BRACKET) {
    return {
      ...common,
      lowerAmount: num(form.lowerAmount, '구간 시작'),
      upperAmount: num(form.upperAmount, '구간 끝'),
      rate: num(form.rate, '비율'),
      baseAmount: num(form.baseAmount, '누적액') ?? 0,
      ...(isAdd && { taxYear: row.taxYear, code: row.bracketType }),
    };
  }
  return {
    ...common,
    name: form.name?.trim() ?? '',
    minAge: num(form.minAge, '최소 나이'),
    maxAge: num(form.maxAge, '최대 나이'),
    maxMilitaryMonths: num(form.maxMilitaryMonths, '군 복무 인정 개월'),
    companySize: form.companySize || null,
    salaryLimit: num(form.salaryLimit, '총급여 조건'),
    homelessHeadReqYn: form.homelessHeadReqYn === 'Y' ? 'Y' : 'N',
    rate: num(form.rate, '감면율'),
    limitAmount: num(form.limitAmount, '한도'),
    periodYears: num(form.periodYears, '적용 기간'),
  };
}

// [추가 11/26 고은] 꼭 채워야 하는 칸 (서버 checkValues · @NotBlank와 같은 규칙) · 화면은 이 칸에 빨간 * 표시
export const REQUIRED_FIELDS = {
  DEDUCTION_TYPE: ['name', 'rate', 'salaryMin', 'sourceNote'],
  TAX_BRACKET: ['lowerAmount', 'rate', 'baseAmount', 'sourceNote'],
  BENEFIT_CONDITION: ['name', 'homelessHeadReqYn', 'sourceNote'],
};

/** 비어 있는 필수 칸 이름 목록 (없으면 빈 배열) · 저장 전에 화면이 빨간 테두리로 표시 */
export function missingRequired(targetType, form = {}) {
  return (REQUIRED_FIELDS[targetType] ?? []).filter((key) => !String(form[key] ?? '').trim());
}

// [추가 11/26 고은] 이력의 DB 칸 이름 → 수정 폼 칸 이름 (되돌리기용) · 여기 없는 칸은 되돌리기 버튼을 안 보여요
export const HISTORY_FIELD = {
  DEDUCTION_TYPE: {
    TYPE_NAME: 'name',
    RATE: 'rate',
    LIMIT_AMOUNT: 'limitAmount',
    SALARY_MIN: 'salaryMin',
    SALARY_MAX: 'salaryMax',
    SOURCE_NOTE: 'sourceNote',
  },
  TAX_BRACKET: {
    LOWER_AMOUNT: 'lowerAmount',
    UPPER_AMOUNT: 'upperAmount',
    RATE: 'rate',
    BASE_AMOUNT: 'baseAmount',
    SOURCE_NOTE: 'sourceNote',
  },
  BENEFIT_CONDITION: {
    BENEFIT_NAME: 'name',
    MIN_AGE: 'minAge',
    MAX_AGE: 'maxAge',
    MAX_MILITARY_MONTHS: 'maxMilitaryMonths',
    COMPANY_SIZE: 'companySize',
    SALARY_LIMIT: 'salaryLimit',
    HOMELESS_HEAD_REQ_YN: 'homelessHeadReqYn',
    REDUCTION_RATE: 'rate',
    LIMIT_AMOUNT: 'limitAmount',
    PERIOD_YEARS: 'periodYears',
    SOURCE_NOTE: 'sourceNote',
  },
};

/**
 * [추가 11/26 고은] 이력 한 줄의 「이전 값」으로 되돌리는 수정 폼 만들기
 * - 지금 행의 값으로 폼을 채운 뒤, 그 칸 하나만 이전 값으로 바꿔요 → 관리자가 확인하고 [저장]하면 PUT
 * - 되돌리기도 「수정」이라 트리거가 새 이력을 한 줄 더 남겨요 (누가 언제 되돌렸는지 남음)
 * - 바꿀 수 없는 칸(연도 · 코드 등)의 이력이면 null
 */
export function formForRevert(targetType, row, history) {
  const key = HISTORY_FIELD[targetType]?.[history?.columnName];
  if (!key) return null;
  const form = formFromRow(targetType, row);
  form[key] = history.oldValue ?? '';
  return { form, key };
}

/** 행의 번호 (테이블마다 이름이 달라요) */
export function idOf(targetType, row = {}) {
  if (targetType === TARGET.DEDUCTION_TYPE) return row.typeId;
  if (targetType === TARGET.TAX_BRACKET) return row.bracketId;
  return row.benefitId;
}

/** 수정(id 있음) 또는 행 추가(id 없음) · body는 서버 TaxCriteriaRequestDTO 모양 */
export function saveTaxCriteria(body, options = {}) {
  if (!TARGET[body?.targetType]) throw apiError('VALIDATION', '기준값 구분을 확인해 주세요');
  if (!String(body.sourceNote ?? '').trim()) {
    throw apiError('VALIDATION', '근거(법 조문 · 신고안내 쪽수)를 입력해 주세요');
  }
  if (body.rate !== null && body.rate !== undefined && (body.rate < 0 || body.rate > 100)) {
    throw apiError('VALIDATION', '비율은 0~100 사이로 입력해 주세요');
  }
  return apiRequest(BASE, {
    ...options,
    method: body.id ? 'PUT' : 'POST',
    body,
    offlineMessage: OFFLINE_MESSAGE,
  });
}

/** 새 연도 추가 (sourceYear 기준값을 targetYear로 복사) */
export function copyTaxCriteriaYear(sourceYear, targetYear, options = {}) {
  if (!isYear(sourceYear) || !isYear(targetYear))
    throw apiError('VALIDATION', '연도를 확인해 주세요');
  if (Number(sourceYear) === Number(targetYear)) {
    throw apiError('VALIDATION', '복사할 연도와 새 연도가 같아요');
  }
  return apiRequest(`${BASE}/years`, {
    ...options,
    method: 'POST',
    body: { sourceYear: Number(sourceYear), targetYear: Number(targetYear) },
    offlineMessage: OFFLINE_MESSAGE,
  });
}

/** 한 행의 변경 이력 */
export async function fetchCriteriaHistory(targetType, id, options = {}) {
  if (!TARGET[targetType]) throw apiError('VALIDATION', '기준값 구분을 확인해 주세요');
  const params = new URLSearchParams({ targetType, id: String(id) });
  const data = await apiRequest(`${BASE}/history?${params}`, {
    ...options,
    offlineMessage: OFFLINE_MESSAGE,
  });
  return Array.isArray(data) ? data : [];
}

/** 표에 보일 금액 (1234567 → 1,234,567 · 비어 있으면 대신 보일 글자) */
export function formatAmount(value, empty = '-') {
  return value === null || value === undefined ? empty : Number(value).toLocaleString('ko-KR');
}
