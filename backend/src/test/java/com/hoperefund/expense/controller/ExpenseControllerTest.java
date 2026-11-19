package com.hoperefund.expense.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hoperefund.common.exception.BadRequestException;
import com.hoperefund.common.exception.NotFoundException;
import com.hoperefund.expense.dto.ExpenseItemDTO;
import com.hoperefund.expense.dto.ExpenseTotalDTO;
import com.hoperefund.expense.dto.UploadHistoryDTO;
import com.hoperefund.expense.service.ExpenseService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 거래 내역 조회 · 삭제 API 테스트 (요청 · 응답 모양 · 오류 코드)
 *
 * <p>@WebMvcTest: Controller와 공통 오류 처리기만 띄워서 가짜 HTTP 요청으로 확인 (DB 없이)
 *
 * <p>jsonPath("$.data[0].칸"): 응답 JSON에서 그 칸의 값을 꺼내 비교
 */
@WebMvcTest(ExpenseController.class)
@AutoConfigureMockMvc(addFilters = false)
class ExpenseControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ExpenseService expenseService;

    @Test
    void 업로드_이력_200() throws Exception {
        when(expenseService.getUploads(1L))
                .thenReturn(
                        List.of(
                                UploadHistoryDTO.builder()
                                        .uploadId(10L)
                                        .fileName("a.csv")
                                        .rowCount(3)
                                        .uploadedAt(LocalDateTime.of(2026, 11, 14, 18, 40))
                                        .build()));

        mockMvc.perform(get("/api/card-uploads").header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].uploadId").value(10))
                .andExpect(jsonPath("$.data[0].fileName").value("a.csv"))
                .andExpect(jsonPath("$.data[0].rowCount").value(3))
                .andExpect(jsonPath("$.data[0].uploadedAt").exists());
    }

    @Test
    void 거래_목록에_분류_근거가_같이_나감() throws Exception {
        ExpenseItemDTO row =
                ExpenseItemDTO.builder()
                        .expenseId(5L)
                        .uploadId(10L)
                        .usedDate(LocalDate.of(2026, 3, 2))
                        .merchantName("㈜서울교통공사")
                        .payMethod("CREDIT")
                        .amount(1_550L)
                        .categoryCode("TRANSIT")
                        .categoryName("대중교통")
                        .listId(13L)
                        .matchType("EXACT")
                        .matchedName("서울교통공사")
                        .sourceName("대중교통 목록")
                        .build();
        when(expenseService.getExpenses(1L, 2026, null)).thenReturn(List.of(row));

        mockMvc.perform(get("/api/expenses").param("year", "2026").header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].usedDate").value("2026-03-02"))
                .andExpect(jsonPath("$.data[0].categoryCode").value("TRANSIT"))
                .andExpect(jsonPath("$.data[0].matchType").value("EXACT"))
                .andExpect(jsonPath("$.data[0].matchedName").value("서울교통공사"))
                .andExpect(jsonPath("$.data[0].sourceName").value("대중교통 목록"));
    }

    @Test
    void 업로드번호로_거래_조회() throws Exception {
        when(expenseService.getExpenses(1L, null, 10L)).thenReturn(List.of());

        mockMvc.perform(get("/api/expenses").param("uploadId", "10").header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void 남의_업로드로_거래_조회하면_404() throws Exception {
        when(expenseService.getExpenses(1L, null, 99L))
                .thenThrow(new NotFoundException("업로드 내역을 찾을 수 없어요"));

        mockMvc.perform(get("/api/expenses").param("uploadId", "99").header("X-Member-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void 연도도_업로드도_없으면_400() throws Exception {
        when(expenseService.getExpenses(1L, null, null))
                .thenThrow(new BadRequestException("연도나 업로드 중 하나를 골라 주세요"));

        mockMvc.perform(get("/api/expenses").header("X-Member-Id", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"));
    }

    @Test
    void 합계는_분류와_결제수단별() throws Exception {
        when(expenseService.getTotals(1L, 2026))
                .thenReturn(
                        List.of(
                                ExpenseTotalDTO.builder()
                                        .categoryId(2L)
                                        .categoryCode("TRANSIT")
                                        .categoryName("대중교통")
                                        .payMethod("CREDIT")
                                        .totalAmount(1_330_820L)
                                        .count(615)
                                        .build()));

        mockMvc.perform(get("/api/expense-totals").param("year", "2026").header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].categoryId").value(2))
                .andExpect(jsonPath("$.data[0].payMethod").value("CREDIT"))
                .andExpect(jsonPath("$.data[0].totalAmount").value(1_330_820))
                .andExpect(jsonPath("$.data[0].count").value(615));
    }

    @Test
    void 합계에_연도가_없으면_400() throws Exception {
        mockMvc.perform(get("/api/expense-totals").header("X-Member-Id", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"));
        verifyNoInteractions(expenseService);
    }

    @Test
    void 삭제_성공이면_204_본문_없음() throws Exception {
        mockMvc.perform(delete("/api/card-uploads/10").header("X-Member-Id", "1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(expenseService).deleteUpload(1L, 10L);
    }

    @Test
    void 남의_업로드를_삭제하면_404() throws Exception {
        // doThrow: 돌려주는 값이 없는(void) 메서드가 예외를 던지게 만들 때 쓰는 방법
        doThrow(new NotFoundException("업로드 내역을 찾을 수 없어요"))
                .when(expenseService)
                .deleteUpload(1L, 99L);

        mockMvc.perform(delete("/api/card-uploads/99").header("X-Member-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void 회원번호_헤더가_없으면_401() throws Exception {
        mockMvc.perform(get("/api/card-uploads")).andExpect(status().isUnauthorized());
        verify(expenseService, never()).getUploads(anyLong());
    }
}
