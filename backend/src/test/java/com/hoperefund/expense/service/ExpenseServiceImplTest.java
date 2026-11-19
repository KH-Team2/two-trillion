package com.hoperefund.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hoperefund.common.exception.BadRequestException;
import com.hoperefund.common.exception.NotFoundException;
import com.hoperefund.expense.dto.ExpenseItemDTO;
import com.hoperefund.expense.dto.ExpenseTotalDTO;
import com.hoperefund.expense.mapper.ExpenseMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ExpenseServiceImpl 테스트 (DB 없이)
 *
 * <p>Mockito: SQL(ExpenseMapper)을 가짜(mock)로 바꿔서 Service의 확인 · 날짜 계산 · 404 처리만 본다.
 *
 * <p>verify(가짜).메서드(값): 그 메서드가 그 값으로 불렸는지 확인
 */
@ExtendWith(MockitoExtension.class)
class ExpenseServiceImplTest {

    @Mock private ExpenseMapper expenseMapper;

    @InjectMocks private ExpenseServiceImpl expenseService;

    private static final long MEMBER = 1L;
    private static final LocalDate JAN_1_2026 = LocalDate.of(2026, 1, 1);
    private static final LocalDate JAN_1_2027 = LocalDate.of(2027, 1, 1);

    // ── 거래 목록 ──

    @Test
    void 연도로_조회하면_그해_1월1일부터_다음해_1월1일_전까지() {
        List<ExpenseItemDTO> rows = List.of(ExpenseItemDTO.builder().expenseId(5L).build());
        when(expenseMapper.findExpenses(MEMBER, null, JAN_1_2026, JAN_1_2027)).thenReturn(rows);

        assertThat(expenseService.getExpenses(MEMBER, 2026, null)).isEqualTo(rows);
    }

    @Test
    void 업로드로_조회하면_내_업로드인지_먼저_확인하고_기간_조건은_없음() {
        when(expenseMapper.countMyUpload(10L, MEMBER)).thenReturn(1);
        when(expenseMapper.findExpenses(MEMBER, 10L, null, null)).thenReturn(List.of());

        assertThat(expenseService.getExpenses(MEMBER, null, 10L)).isEmpty();
    }

    @Test
    void 남의_업로드로_조회하면_404() {
        when(expenseMapper.countMyUpload(99L, MEMBER)).thenReturn(0);

        assertThatThrownBy(() -> expenseService.getExpenses(MEMBER, null, 99L))
                .isInstanceOf(NotFoundException.class);
        // 거래 목록 SQL은 아예 안 부름
        verify(expenseMapper, never()).findExpenses(anyLong(), any(), any(), any());
    }

    @Test
    void 연도와_업로드가_둘_다_없으면_400() {
        assertThatThrownBy(() -> expenseService.getExpenses(MEMBER, null, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void 이상한_연도면_400() {
        assertThatThrownBy(() -> expenseService.getExpenses(MEMBER, 999999, null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> expenseService.getTotals(MEMBER, 1999))
                .isInstanceOf(BadRequestException.class);
    }

    // ── 합계 ──

    @Test
    void 합계는_연도_범위로_조회() {
        List<ExpenseTotalDTO> totals =
                List.of(
                        ExpenseTotalDTO.builder()
                                .categoryId(2L)
                                .categoryCode("TRANSIT")
                                .payMethod("CREDIT")
                                .totalAmount(1_550L)
                                .count(1)
                                .build());
        when(expenseMapper.findTotals(MEMBER, JAN_1_2026, JAN_1_2027)).thenReturn(totals);

        assertThat(expenseService.getTotals(MEMBER, 2026)).isEqualTo(totals);
    }

    // ── 삭제 ──

    @Test
    void 내_업로드면_삭제() {
        when(expenseMapper.deleteMyUpload(10L, MEMBER)).thenReturn(1);

        expenseService.deleteUpload(MEMBER, 10L);

        verify(expenseMapper).deleteMyUpload(10L, MEMBER);
    }

    @Test
    void 없거나_남의_업로드를_삭제하면_404() {
        when(expenseMapper.deleteMyUpload(99L, MEMBER)).thenReturn(0);

        assertThatThrownBy(() -> expenseService.deleteUpload(MEMBER, 99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(ExpenseServiceImpl.UPLOAD_NOT_FOUND);
    }

    @Test
    void 업로드_이력은_mapper_결과_그대로() {
        when(expenseMapper.findUploads(MEMBER)).thenReturn(List.of());

        assertThat(expenseService.getUploads(MEMBER)).isEmpty();
        verify(expenseMapper).findUploads(MEMBER);
    }

    @Test
    void 연도만_있을때_업로드_확인_SQL은_안_부름() {
        when(expenseMapper.findExpenses(MEMBER, null, JAN_1_2026, JAN_1_2027))
                .thenReturn(List.of());

        expenseService.getExpenses(MEMBER, 2026, null);

        verify(expenseMapper, never()).countMyUpload(anyLong(), anyLong());
        verify(expenseMapper).findExpenses(MEMBER, null, JAN_1_2026, JAN_1_2027);
    }
}
