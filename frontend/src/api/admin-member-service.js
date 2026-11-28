// 관리자 회원 관리 API 연결 (F-20 · F-01-06 · F-08-02 ① · S-08) · [신규 11/23 고은]
// - GET /api/admin/members?keyword=&role=&status=&page=&size=  : 이메일 일부를 가린 회원 목록 (+ 쪽 정보 meta)
// - PUT /api/admin/members/{id}/role  본문 { role: 'ADMIN' | 'USER' }  : 관리자 권한 부여 · 해제
//   관리자(ROLE=ADMIN) 토큰만 통과 (아니면 403) · 조회 · 변경할 때마다 서버가 ACCESS_LOG에 기록해요
// - 실패 코드(409): SELF_DEMOTE(자기 자신 해제) · GENDER_REQUIRED(성별 없는 관리자) · SAME_ROLE · WITHDRAWN_MEMBER
import { apiError, apiRequest } from './api-client.js';

// DB 값 → 화면 이름 (CK_MEMBER_ROLE · CK_MEMBER_STATUS와 같은 값)
export const ROLE_LABEL = { USER: '일반 회원', ADMIN: '관리자' };
export const STATUS_LABEL = { ACTIVE: '사용 중', WITHDRAWN: '탈퇴' };

// 한 쪽에 보여 줄 회원 수 · 서버 최대값(@Max(100))
export const MEMBER_PAGE_SIZE = 20;
const MAX_SIZE = 100;

const OFFLINE_MESSAGE = '관리자 서버에 연결할 수 없어요.';

/**
 * 화면 검색 조건 → 주소 뒤에 붙일 글자(?keyword=…&page=1&size=20)
 * - 비어 있는 조건은 빼요 (서버가 「조건 없음 = 전체」로 처리)
 * - 권한 · 상태는 정해진 값만 · 아니면 서버에 보내기 전에 막아요
 */
export function memberQuery({
  keyword = '',
  role = '',
  status = '',
  page = 1,
  size = MEMBER_PAGE_SIZE,
} = {}) {
  const word = String(keyword ?? '').trim();
  if (role && !ROLE_LABEL[role]) throw apiError('VALIDATION', '권한을 확인해 주세요');
  if (status && !STATUS_LABEL[status]) throw apiError('VALIDATION', '계정 상태를 확인해 주세요');
  const pageNo = Number(page);
  const sizeNo = Number(size);
  if (!Number.isInteger(pageNo) || pageNo < 1)
    throw apiError('VALIDATION', '쪽 번호를 확인해 주세요');
  if (!Number.isInteger(sizeNo) || sizeNo < 1 || sizeNo > MAX_SIZE) {
    throw apiError('VALIDATION', `한 쪽에 1~${MAX_SIZE}명까지 볼 수 있어요`);
  }
  const params = new URLSearchParams();
  if (word) params.set('keyword', word);
  if (role) params.set('role', role);
  if (status) params.set('status', status);
  params.set('page', String(pageNo));
  params.set('size', String(sizeNo));
  return `?${params.toString()}`;
}

/** 회원 한 쪽 + 쪽 정보 · 비어 있으면 rows는 빈 배열 */
export async function fetchMembers(filters = {}, options = {}) {
  const { data, meta } = await apiRequest(`/api/admin/members${memberQuery(filters)}`, {
    ...options,
    withMeta: true,
    offlineMessage: OFFLINE_MESSAGE,
  });
  return {
    rows: Array.isArray(data) ? data : [],
    meta: meta ?? {
      page: Number(filters.page) || 1,
      size: MEMBER_PAGE_SIZE,
      total: 0,
      totalPages: 1,
    },
  };
}

/** 권한 바꾸기 · role은 'ADMIN'(부여) 또는 'USER'(해제) */
export function updateMemberRole(memberId, role, options = {}) {
  if (!Number.isInteger(Number(memberId)) || Number(memberId) <= 0) {
    throw apiError('VALIDATION', '회원 번호를 확인해 주세요');
  }
  if (!ROLE_LABEL[role]) throw apiError('VALIDATION', '바꿀 권한을 확인해 주세요');
  return apiRequest(`/api/admin/members/${encodeURIComponent(memberId)}/role`, {
    ...options,
    method: 'PUT',
    body: { role },
    offlineMessage: OFFLINE_MESSAGE,
  });
}
