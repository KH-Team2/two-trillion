import { getDefaultTaxYear } from '../utils/tax-year.js';
// 업로드한 거래 묶음(업로드 단위) · 중복 표시 · 집계를 다루는 함수들입니다.
// [수정 11/23 고은] 브라우저에서 파일을 직접 읽고 예시 상호명 목록으로 분류하던 함수 삭제
//   (columnAliases · normalizedDate · parseUploadTable · parseUploadCSV · parseUploadFile · merchantService 사용)
//   이유: 체험판 기능 삭제 결정 · 업로드 · 분류는 서버(POST /api/card-uploads)가 해요 (card-upload-service.js)
export const payCode = (label) =>
  ({
    신용카드: 'CREDIT',
    체크카드: 'CHECK',
    현금영수증: 'CASH',
    CREDIT: 'CREDIT',
    CHECK: 'CHECK',
    CASH: 'CASH',
  })[label];
export const isCounted = (r) =>
  !['pending', 'excluded'].includes(r.duplicateState || '') &&
  ['신용카드', '체크카드', '현금영수증'].includes(r.paymentMethod || '');
export const transactionKey = (r) =>
  [r.date, r.merchant.replace(/\s/g, '').toLowerCase(), r.amount, r.paymentMethod].join('|');
export function flagDuplicates(existing, incoming) {
  const seen = new Map(
    existing.filter(isCounted).map((r) => [transactionKey(r), `${r.uploadId || 'legacy'}:${r.id}`]),
  );
  return incoming.map((r) => {
    const key = transactionKey(r),
      duplicateOf = seen.get(key);
    if (duplicateOf) return { ...r, duplicateState: 'pending', duplicateOf };
    if (r.paymentMethod !== '확인 필요') seen.set(key, `${r.uploadId}:${r.id}`);
    return r;
  });
}
export function uploadFiles(meta, rows) {
  if (meta?.files) return meta.files;
  if (!rows.length) return [];
  return [
    {
      id: 'legacy',
      name: meta?.name || '이전 업로드',
      count: rows.length,
      at: meta?.at || new Date().toISOString(),
      source: 'card',
      status: rows.some((r) => !isCounted(r)) ? '확인 필요' : '업로드 완료',
    },
  ];
}
export function makeMeta(files) {
  return files.length
    ? {
        name: files.map((f) => f.name).join(', '),
        count: files.reduce((s, f) => s + f.count, 0),
        at: files[files.length - 1].at,
        files,
      }
    : null;
}
export function removeUpload(rows, files, id) {
  return {
    rows: rows.filter((r) => (r.uploadId || 'legacy') !== id),
    meta: makeMeta(files.filter((f) => f.id !== id)),
  };
}
export function uploadTotals(rows, taxYear = getDefaultTaxYear()) {
  return rows.reduce(
    (sum, r) => {
      if (
        !isCounted(r) ||
        Number(r.date.slice(0, 4)) !== taxYear ||
        ['공제 제외', '공제 대상 아님'].includes(r.category)
      )
        return sum;
      if (r.paymentMethod === '체크카드') sum.debit += r.amount;
      else if (r.paymentMethod === '현금영수증') sum.cash += r.amount;
      else if (r.paymentMethod === '신용카드') sum.credit += r.amount;
      return sum;
    },
    { credit: 0, debit: 0, cash: 0 },
  );
}
export function transactionExamples(rows, category, limit = 15) {
  const all = rows
    .filter(
      (r) =>
        r.source === 'upload' &&
        (!category ||
          (r.category === '공제 제외' ? '공제 대상 아님' : r.category) ===
            (category === '공제 제외' ? '공제 대상 아님' : category)),
    )
    .sort((a, b) => b.date.localeCompare(a.date));
  return {
    count: all.length,
    total: all.reduce((s, r) => s + r.amount, 0),
    countedTotal: all.filter(isCounted).reduce((s, r) => s + r.amount, 0),
    examples: all.slice(0, limit),
  };
}
export function uploadBlocker(rows) {
  return rows.some((r) => r.paymentMethod === '확인 필요' || !r.paymentMethod)
    ? '결제수단을 알 수 없는 파일이 있습니다. 파일 목록에서 신용카드·체크카드를 선택하거나 해당 파일을 삭제해주세요.'
    : '';
}
