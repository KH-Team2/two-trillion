import { apiError, apiRequest } from './api-client.js';

// 동시에 같은 계산 결과의 가이드를 중복 요청하지 않도록 관리
const pending = new Map();

export function fetchTaxGuides(predictionId) {
  if (!Number.isInteger(predictionId) || predictionId < 1) {
    return Promise.reject(apiError('VALIDATION', '계산 결과 번호를 확인해주세요.'));
  }

  if (pending.has(predictionId)) {
    return pending.get(predictionId);
  }

  const request = apiRequest(`/api/predictions/${predictionId}/recommendations`, {
    method: 'POST',
    offlineMessage: '맞춤 절세 가이드 서버에 연결할 수 없어요.',
  })
    .then((result) => {
      if (!Array.isArray(result?.items)) {
        throw apiError('INVALID_RESPONSE', '절세 가이드 응답 형식이 올바르지 않습니다.');
      }

      return result.items;
    })
    .finally(() => pending.delete(predictionId));

  pending.set(predictionId, request);
  return request;
}
