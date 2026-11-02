// 청년 혜택 자격 진단 API 연결 (F-03-02 · S-05) · GET /api/benefits/{taxYear}
// 판정은 서버(계산 서버의 BENEFIT_CONDITION 규칙)가 해요. 화면은 받은 결과를 그대로 보여 주기만 해요.
const env = import.meta.env || {};

/** 서버 응답 상태값 → 화면 표시 (true 해당 · false 비해당 · null 정보 부족) */
export function benefitStatus(eligible) {
  if (eligible === true) return { label: '해당', tone: 'green' };
  if (eligible === false) return { label: '비해당', tone: 'gray' };
  return { label: '정보 부족', tone: 'amber' };
}

// 준비 서류 안내 (DB에 저장하지 않는 고정 문구 · 기능명세서 F-03-02)
// 근거: 국세청 「2025 원천징수의무자를 위한 연말정산 신고안내」(2025.12) 쪽수
export const PREPARE_DOCS = {
  SME_YOUTH: {
    items: [
      '중소기업 취업자 소득세 감면신청서 (「조특법 시행규칙」 별지 제11호 서식)',
      '병역복무기간을 증명하는 서류 (군 복무 기간을 빼고 나이를 계산할 때)',
    ],
    note: '회사(원천징수의무자)에 제출 · 신고안내 153·396p',
  },
  RENT: {
    items: [
      '주민등록표등본',
      '임대차계약증서 사본',
      '월세액 지급 증명서류 (현금영수증, 계좌이체 영수증, 무통장입금증 등)',
      '월세액 소득·세액공제 명세서',
    ],
    note: '신고안내 397p',
  },
  HOUSING_SAVING: {
    items: [
      '무주택 확인서 (다음 연도 2월 말까지 저축취급기관에 제출)',
      '주택청약종합저축 납입증명서',
    ],
    note: '신고안내 122~123p',
  },
};

/** 서버 오류 응답 {error, message}을 Error로 바꿔요 (code에 error 값) */
function apiError(code, message) {
  const error = new Error(message);
  error.code = code;
  return error;
}

/** 응답 모양을 검사해요. 빠진 값을 임의로 채우지 않아요. */
export function parseBenefitResponse(raw) {
  if (raw?.error) throw apiError(raw.error, raw.message || '혜택 진단 결과를 불러올 수 없어요.');
  if (!raw || !Number.isInteger(raw.taxYear) || !Array.isArray(raw.results)) {
    throw apiError('INVALID_RESPONSE', '혜택 진단 결과 형식이 올바르지 않아요.');
  }
  return {
    taxYear: raw.taxYear,
    results: raw.results.map((item) => {
      if (typeof item?.benefitCode !== 'string' || ![true, false, null].includes(item.eligible)) {
        throw apiError('INVALID_RESPONSE', '혜택 진단 결과 형식이 올바르지 않아요.');
      }
      return {
        benefitCode: item.benefitCode,
        benefitName: String(item.benefitName || item.benefitCode),
        eligible: item.eligible,
        reasons: Array.isArray(item.reasons) ? item.reasons.map(String) : [],
        sourceNote: typeof item.sourceNote === 'string' ? item.sourceNote : '',
      };
    }),
  };
}

/**
 * 혜택 진단 요청.
 * - 회원 구분: 로그인(Spring Security) 연결 전까지 개발용 .env의 VITE_DEV_MEMBER_ID를 X-Member-Id 헤더로 보내요.
 *   운영에서는 이 값을 비워 두고, 서버가 로그인 정보로 회원을 정해요.
 * - 서버에 연결할 수 없으면 code가 'OFFLINE'인 오류를 던져요 (화면은 체험 판정으로 돌아가요).
 */
export async function fetchBenefits(taxYear, { fetchImpl = globalThis.fetch, memberId } = {}) {
  if (!Number.isInteger(taxYear) || taxYear < 2000 || taxYear > 2100) {
    throw apiError('VALIDATION', '귀속 연도를 확인해주세요.');
  }
  const id = memberId ?? env.VITE_DEV_MEMBER_ID;
  const headers = { Accept: 'application/json' };
  if (id && /^\d+$/.test(String(id))) headers['X-Member-Id'] = String(id);

  let response;
  try {
    response = await fetchImpl(`/api/benefits/${taxYear}`, { headers, credentials: 'same-origin' });
  } catch {
    throw apiError('OFFLINE', '혜택 진단 서버에 연결할 수 없어요.');
  }
  let body = null;
  try {
    body = await response.json();
  } catch {
    // JSON이 아니면 서버가 꺼져 있거나(개발 프록시 오류) 다른 서버가 응답한 거예요
    throw apiError('OFFLINE', '혜택 진단 서버에 연결할 수 없어요.');
  }
  if (!response.ok) {
    throw apiError(
      body?.error || `HTTP_${response.status}`,
      body?.message || '혜택 진단 결과를 불러올 수 없어요.',
    );
  }
  return parseBenefitResponse(body);
}
