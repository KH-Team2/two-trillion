// 그룹 비교 통계 API 연결 (F-06-02 · 「나와 비슷한 사람들은」) · GET /api/group-stats/{taxYear}
// 내가 속한 그룹은 서버가 찾아요 (회원 성별 · 생년월일 · 저장된 총급여 기준). 화면은 받은 값을 보여 주기만 해요.
// 요청 · 봉투 풀기 · 오류 처리 · 개발용 회원번호는 api-client.js 공통 함수를 써요.
import { apiRequest, checkTaxYear } from './api-client.js';

/**
 * 본인이 속한 그룹 두 개의 국세통계 평균 조회
 * 응답: { taxYear, ageGroup, salaryGroup } · 못 찾은 그룹은 null (성별 미입력 · 소득 미저장 등)
 */
export async function fetchGroupStats(taxYear, options = {}) {
  checkTaxYear(taxYear);
  return apiRequest(`/api/group-stats/${taxYear}`, {
    ...options,
    offlineMessage: '통계 서버에 연결할 수 없어요.',
  });
}
