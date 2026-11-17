// 카드 · 홈택스 파일 업로드 API 연결 (F-02-02 · TT-112)
// - POST /api/card-uploads : 파일을 서버로 보냄 → 서버(계산 서버)가 읽고 분류해서 DB에 저장
// - GET  /api/expenses?uploadId= : 방금 저장된 거래 목록 + 분류 근거를 다시 받아 옴
// 브라우저에서 파일을 직접 읽던 방식(expense-service.js의 parseUploadFile)과 달리,
// 여기서 올린 거래는 DB(EXPENSE)에 들어가서 세액 계산에도 반영돼요.
import { apiError, apiRequest } from './api-client.js';

// 서버 결제수단 코드 → 화면 이름 (기존 화면이 쓰는 이름과 같게)
const PAY_LABEL = { CREDIT: '신용카드', CHECK: '체크카드', CASH: '현금영수증' };

// 분류 근거의 일치 방식 → 화면 문구
const MATCH_LABEL = { EXACT: '이름 일치', CONTAINS: '이름 포함' };

/**
 * 파일 1개 업로드
 *
 * @param {File} file 사용자가 고른 파일
 * @param {{payMethod?: 'CREDIT'|'CHECK', allowDuplicate?: boolean}} choice
 *   - payMethod: 파일에 사용구분 칸이 없을 때 사용자가 고른 결제수단 (없으면 안 보냄)
 *   - allowDuplicate: 중복 경고(409) 뒤 「그래도 올리기」를 눌렀을 때 true
 * @returns 서버 응답 data {uploadId, fileName, rowCount, uploadedAt, categoryCounts}
 *   실패하면 code가 붙은 오류를 던져요 (PAY_METHOD_REQUIRED · DUPLICATE_WARNING · VALIDATION 등)
 */
export function uploadCardFile(file, { payMethod, allowDuplicate = false } = {}, options = {}) {
  // FormData: 파일을 담아 보내는 상자 (input type="file"로 보내는 것과 같은 모양 · multipart)
  const form = new FormData();
  form.append('file', file); // 서버 @RequestPart("file")
  if (payMethod) form.append('payMethod', payMethod);
  if (allowDuplicate) form.append('allowDuplicate', 'true');
  return apiRequest('/api/card-uploads', {
    ...options,
    method: 'POST',
    body: form,
    offlineMessage: '카드 업로드 서버에 연결할 수 없어요.',
  });
}

/** 업로드 1건의 거래 목록 + 분류 근거 (최근 사용일부터) */
export function fetchUploadExpenses(uploadId, options = {}) {
  if (!Number.isInteger(Number(uploadId))) throw apiError('VALIDATION', '업로드 번호를 확인해주세요.');
  return apiRequest(`/api/expenses?uploadId=${encodeURIComponent(uploadId)}`, {
    ...options,
    offlineMessage: '거래 내역 서버에 연결할 수 없어요.',
  });
}

/** 분류 근거 문구 · 예) "서울교통공사 · 한국교통안전공단_철도운영기관 현황 (이름 포함)" */
export function basisText(item) {
  if (!item.listId) return '공공데이터 목록에 없음 → 일반';
  const how = MATCH_LABEL[item.matchType] || item.matchType || '';
  return `${item.matchedName} · ${item.sourceName}${how ? ` (${how})` : ''}`;
}

/**
 * 서버 거래 1건 → 기존 화면이 쓰는 거래 모양
 * (목록 · 합계 · 분류 확인 표가 이 모양을 그대로 쓰기 때문에 이름을 맞춰 줌)
 *
 * @param item  GET /api/expenses 의 한 줄
 * @param upload POST /api/card-uploads 응답 (uploadId · fileName)
 */
export function toUploadRow(item, upload) {
  return {
    id: item.expenseId,
    date: item.usedDate,
    merchant: item.merchantName,
    amount: item.amount,
    category: item.categoryName, // 일반 · 대중교통 · 전통시장 · 문화체육 · 공제 대상 아님
    classificationBasis: basisText(item),
    paymentMethod: PAY_LABEL[item.payMethod] || '확인 필요',
    PAY_METHOD: item.payMethod,
    source: 'upload',
    uploadId: String(upload.uploadId), // 파일 목록의 id와 같은 모양(글자)으로
    uploadName: upload.fileName,
    serverSaved: true, // DB에 저장된 거래라는 표시
  };
}

// ───────── [추가 11/17 고은] TT-115: 업로드 이력 조회 · 삭제 ─────────

/** 내 업로드 이력 (최근 것부터) · [{uploadId, fileName, rowCount, uploadedAt}] */
export function fetchUploads(options = {}) {
  return apiRequest('/api/card-uploads', {
    ...options,
    offlineMessage: '업로드 이력 서버에 연결할 수 없어요.',
  });
}

/**
 * 업로드 1건 삭제 (그 업로드의 거래도 서버가 같이 지움)
 * - 성공하면 204(본문 없음) → null
 * - 없거나 남의 업로드면 code 'NOT_FOUND' 오류
 */
export function deleteUpload(uploadId, options = {}) {
  if (!Number.isInteger(Number(uploadId))) {
    throw apiError('VALIDATION', '업로드 번호를 확인해주세요.');
  }
  return apiRequest(`/api/card-uploads/${encodeURIComponent(uploadId)}`, {
    ...options,
    method: 'DELETE',
    offlineMessage: '업로드 삭제 서버에 연결할 수 없어요.',
  });
}

/**
 * 서버에 저장된 내 업로드 전체를 화면 모양으로 불러오기
 * (업로드 이력 1번 + 업로드마다 거래 목록 1번씩 요청)
 *
 * @returns {{rows: 화면 거래 목록, files: 화면 파일 목록}}
 */
export async function loadServerUploads(options = {}) {
  const uploads = await fetchUploads(options);
  // Promise.all: 업로드마다 거래 목록 요청을 「동시에」 보내고, 전부 끝날 때까지 기다림
  // (하나씩 차례로 기다리는 것보다 빠름 · 하나라도 실패하면 전체가 실패로 처리됨)
  const perUpload = await Promise.all(
    uploads.map(async (upload) => {
      const rows = (await fetchUploadExpenses(upload.uploadId, options)).map((item) =>
        toUploadRow(item, upload),
      );
      // 모든 거래가 현금영수증이면 홈택스 파일로 표시
      const hometax = rows.length > 0 && rows.every((r) => r.PAY_METHOD === 'CASH');
      const file = {
        id: String(upload.uploadId),
        name: upload.fileName,
        count: rows.length,
        at: upload.uploadedAt,
        source: hometax ? 'hometax' : 'card',
        status: '업로드 완료',
        serverSaved: true,
      };
      return { rows, file };
    }),
  );
  return {
    rows: perUpload.flatMap((p) => p.rows), // flatMap: 업로드별 거래 목록을 한 줄로 이어 붙임
    files: perUpload.map((p) => p.file),
  };
}
