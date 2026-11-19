package com.hoperefund.expense.mapper;

import com.hoperefund.expense.dto.ExpenseItemDTO;
import com.hoperefund.expense.dto.ExpenseTotalDTO;
import com.hoperefund.expense.dto.UploadHistoryDTO;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 거래 내역 조회 · 삭제용 SQL 목록 (F-09 · 실제 SQL은 resources/mapper/expense/ExpenseMapper.xml)
 *
 * <p>업로드 저장용 CardUploadMapper와 나눈 이유: 「저장」과 「조회 · 삭제」는 하는 일이 달라서 파일을 나누면 찾기 쉬움
 *
 * <p>모든 SQL에 memberId 조건이 들어감 → 본인 데이터만 보고 지울 수 있음 (백엔드 규칙)
 *
 * <p>날짜 범위 fromDate · toDate: 「fromDate 이상, toDate 미만」 (예: 2026년 = 2026-01-01 이상 2027-01-01 미만)
 */
@Mapper
public interface ExpenseMapper {

    /** 내 업로드 이력 (최근 것부터) */
    List<UploadHistoryDTO> findUploads(@Param("memberId") long memberId);

    /** 내 업로드인지 확인 (내 것이면 1, 없거나 남의 것이면 0) */
    int countMyUpload(@Param("uploadId") long uploadId, @Param("memberId") long memberId);

    /**
     * 내 거래 목록 + 분류 근거 (최근 사용일부터)
     *
     * @param uploadId 이 업로드의 거래만 (null이면 조건 없음)
     * @param fromDate 이 날짜 이상 (null이면 조건 없음)
     * @param toDate 이 날짜 미만 (null이면 조건 없음)
     */
    List<ExpenseItemDTO> findExpenses(
            @Param("memberId") long memberId,
            @Param("uploadId") Long uploadId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    /** 기간 안의 내 거래를 분류 · 결제수단별로 더한 합계 */
    List<ExpenseTotalDTO> findTotals(
            @Param("memberId") long memberId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    /**
     * 내 업로드 1건 삭제 · 그 업로드의 지출(EXPENSE)도 DB가 같이 지움 (ON DELETE CASCADE)
     *
     * @return 지운 업로드 행 수 (내 것이면 1, 없거나 남의 것이면 0)
     */
    int deleteMyUpload(@Param("uploadId") long uploadId, @Param("memberId") long memberId);
}
