package com.hoperefund.expense.service;

import com.hoperefund.common.exception.BadRequestException;
import com.hoperefund.common.exception.NotFoundException;
import com.hoperefund.expense.dto.ExpenseItemDTO;
import com.hoperefund.expense.dto.ExpenseTotalDTO;
import com.hoperefund.expense.dto.UploadHistoryDTO;
import com.hoperefund.expense.mapper.ExpenseMapper;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거래 내역 조회 · 삭제 구현 (F-09)
 *
 * <p>@Transactional(readOnly = true): 이 클래스의 메서드는 기본으로 「읽기만 하는 트랜잭션」 · 지우는 메서드만 따로 일반
 * 트랜잭션으로 바꿈
 */
@Service
@RequiredArgsConstructor // final 칸(ExpenseMapper)을 받는 생성자를 자동으로 만들어 줌 → 스프링이 주입
@Transactional(readOnly = true)
public class ExpenseServiceImpl implements ExpenseService {

    /** 남의 것 · 없는 것 모두 같은 문구 (어느 쪽인지 알려 주지 않으려고) */
    static final String UPLOAD_NOT_FOUND = "업로드 내역을 찾을 수 없어요";

    private final ExpenseMapper expenseMapper;

    @Override
    public List<UploadHistoryDTO> getUploads(long memberId) {
        return expenseMapper.findUploads(memberId);
    }

    @Override
    public List<ExpenseItemDTO> getExpenses(long memberId, Integer year, Long uploadId) {
        // 둘 다 없으면 내 거래 전체가 한꺼번에 나가서 막음 (연도나 업로드 중 하나는 골라야 함)
        if (year == null && uploadId == null) {
            throw new BadRequestException("연도나 업로드 중 하나를 골라 주세요");
        }
        if (year != null) {
            checkYear(year);
        }

        // 업로드를 골랐으면 내 업로드인지 먼저 확인 · 남의 것이면 빈 목록 대신 404
        if (uploadId != null && expenseMapper.countMyUpload(uploadId, memberId) == 0) {
            throw new NotFoundException(UPLOAD_NOT_FOUND);
        }

        // 연도 → 날짜 범위 (예: 2026 → 2026-01-01 이상 2027-01-01 미만) · 연도가 없으면 기간 조건 없음
        LocalDate fromDate = (year == null) ? null : yearStart(year);
        LocalDate toDate = (year == null) ? null : yearStart(year + 1);
        return expenseMapper.findExpenses(memberId, uploadId, fromDate, toDate);
    }

    @Override
    public List<ExpenseTotalDTO> getTotals(long memberId, int year) {
        checkYear(year);
        return expenseMapper.findTotals(memberId, yearStart(year), yearStart(year + 1));
    }

    @Override
    @Transactional // 지우는 작업이라 readOnly를 풀어 줌
    public void deleteUpload(long memberId, long uploadId) {
        // MEMBER_ID 조건이 붙은 DELETE 한 번으로 「내 것인지 확인 + 삭제」를 같이 함
        // 0행이면 없거나 남의 것 → 404
        int deleted = expenseMapper.deleteMyUpload(uploadId, memberId);
        if (deleted == 0) {
            throw new NotFoundException(UPLOAD_NOT_FOUND);
        }
    }

    /**
     * 연도 범위 검사 (2000 ~ 2100 · 계산 서버 요청 taxYear 범위와 같게)
     *
     * <p>999999 같은 오타 연도는 날짜를 만들 때 서버 오류(500)가 나서, 그 전에 400으로 막음
     */
    private static void checkYear(int year) {
        if (year < 2000 || year > 2100) {
            throw new BadRequestException("연도를 확인해 주세요");
        }
    }

    /** 그 해 1월 1일 */
    private static LocalDate yearStart(int year) {
        return LocalDate.of(year, 1, 1);
    }
}
