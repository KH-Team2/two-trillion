package com.hoperefund.expense.service;

import com.hoperefund.expense.dto.ExpenseItemDTO;
import com.hoperefund.expense.dto.ExpenseTotalDTO;
import com.hoperefund.expense.dto.UploadHistoryDTO;
import java.util.List;

/**
 * 거래 내역 조회 · 삭제 (F-09 · F-02-04)
 *
 * <p>남의 데이터 규칙: 남의 업로드 번호로 조회 · 삭제하면 403이 아니라 404(없는 것과 같은 응답)
 * (GlobalExceptionHandler 팀 규칙 · 번호를 바꿔 가며 남의 데이터가 있는지 알아낼 수 없게)
 */
public interface ExpenseService {

    /** 내 업로드 이력 (최근 것부터) */
    List<UploadHistoryDTO> getUploads(long memberId);

    /**
     * 내 거래 목록 + 분류 근거
     *
     * @param year 사용일 연도 (null 가능)
     * @param uploadId 업로드 번호 (null 가능)
     * @throws com.hoperefund.common.exception.BadRequestException 400 year · uploadId 둘 다 없음
     * @throws com.hoperefund.common.exception.NotFoundException 404 없거나 남의 업로드
     */
    List<ExpenseItemDTO> getExpenses(long memberId, Integer year, Long uploadId);

    /** 연도의 분류 · 결제수단별 합계 (계산 서버 cardSpends와 같은 단위) */
    List<ExpenseTotalDTO> getTotals(long memberId, int year);

    /**
     * 업로드 1건 삭제 (그 업로드의 거래도 같이 삭제)
     *
     * @throws com.hoperefund.common.exception.NotFoundException 404 없거나 남의 업로드
     */
    void deleteUpload(long memberId, long uploadId);
}
