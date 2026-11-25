import React from 'react';
import { deductionPresentation, cardDeductionTotal } from '@/api/calculation-contract.js';
const money = (n) => n.toLocaleString('ko-KR');

function mergeCardLimitRows(details) {
  const result = [];
  let mergedRow = null;

  for (const item of details) {
    const isNegativeCardLimit =
      item.typeCode?.startsWith('CARD_LIMIT_') && item.deductionAmount < 0;

    // 일반 공제 항목은 원래 순서대로 유지
    if (!isNegativeCardLimit) {
      result.push(item);
      continue;
    }

    // 첫 번째 음수 한도 조정 항목의 위치에 합계 행 생성
    if (!mergedRow) {
      mergedRow = {
        ...item,
        typeName: '카드 공제 한도 초과분',
        deductionAmount: 0,
        sourceNote: '',
        isMergedCardLimit: true,
      };
      result.push(mergedRow);
    }

    // 음수인 한도 조정액만 합산
    mergedRow.deductionAmount += item.deductionAmount;

    // 각 항목의 계산 근거도 보존
    if (item.sourceNote) {
      mergedRow.sourceNote = [
        ...new Set([mergedRow.sourceNote, item.sourceNote].filter(Boolean)),
      ].join(' / ');
    }
  }

  return result;
}

// 소비 지출 집계와 분리된 계산 응답 표입니다.
// [수정 11/23] calculationConnected(체험용 계산일 때 금액 숨김) 삭제 · 이유: 체험판 기능 삭제 결정 → 항상 서버 계산 결과
export default function DeductionDetails({ details = [] }) {
  const displayDetails = mergeCardLimitRows(details);
  const max = Math.max(1, ...details.map((item) => Math.abs(item.deductionAmount)));
  return (
    <details className="deduction-detail deduction-response">
      <summary className="dash-disclosure">
        <span>
          <b>공제·감면 상세 내역</b>
          <small className="block-note">소득공제액·세액공제액·감면액 확인</small>
        </span>
        <span className="tag blue">계산 결과</span>
      </summary>
      <p className="dash-description">
        계산 결과의 공제 항목입니다. 실제 지출금액과 다르며, 0원 항목과 음수 한도 조정액도 그대로
        표시합니다.
      </p>
      {details.length === 0 ? (
        <p className="empty">제공된 항목별 공제 내역이 없습니다.</p>
      ) : (
        <div className="dash-table-scroll deduction-response-scroll">
          <table className="dash-card-table deduction-response-table">
            <caption className="sr-only">지출 집계와 별도로 제공되는 공제·감면 계산 상세</caption>
            <thead>
              <tr>
                <th scope="col">공제 항목명</th>
                <th scope="col">공제 대상 금액</th>
                <th scope="col">적용 공제액</th>
                <th scope="col">공제 유형</th>
                <th scope="col">적용 근거</th>
              </tr>
            </thead>
            <tbody>
              {displayDetails.map((item, index) => {
                const view = deductionPresentation(item);
                const negative = item.deductionAmount < 0;
                const width = (Math.abs(item.deductionAmount) / max) * 50;

                // 카드 공제 한도 초과분은 음수일 때 이름 변경
                const displayName =
                  item.typeCode?.startsWith('CARD_LIMIT_') && item.deductionAmount < 0
                    ? '카드 공제 한도 초과분'
                    : item.typeName;

                return (
                  <tr key={`${item.typeCode}-${index}`}>
                    <th scope="row">{displayName}</th>
                    <td>{item.isMergedCardLimit ? '—' : `${money(item.baseAmount)}원`}</td>
                    <td>
                      <b className={negative ? 'negative-amount' : ''}>
                        {`${money(item.deductionAmount)}원`}
                      </b>
                      {negative && (
                        <small className="block-note">카드 공제한도 적용에 따른 조정액</small>
                      )}
                      <div
                        className="deduction-signed-track"
                        role="img"
                        aria-label={`${displayName} 공제액 ${money(item.deductionAmount)}원`}
                      >
                        <span
                          style={{
                            left: `${negative ? 50 - width : 50}%`,
                            width: `${width}%`,
                            background: negative ? '#d45b61' : '#2767f5',
                          }}
                        />
                      </div>
                    </td>
                    <td>
                      {view.kindLabel}
                      <small className="block-note">{item.kind}</small>
                    </td>
                    <td>{item.sourceNote || '제공된 근거 없음'}</td>
                  </tr>
                );
              })}
            </tbody>
            {details.some((item) => ['CARD', 'CARD_RULE'].includes(item.kind)) && (
              <tfoot>
                <tr>
                  <th scope="row" colSpan={2}>
                    카드 소득공제 합계 <small>음수 조정액 포함</small>
                  </th>
                  <td colSpan={3}>{`${money(cardDeductionTotal(details))}원`}</td>
                </tr>
              </tfoot>
            )}
          </table>
        </div>
      )}
    </details>
  );
}
