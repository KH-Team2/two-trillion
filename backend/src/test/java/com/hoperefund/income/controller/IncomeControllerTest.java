package com.hoperefund.income.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hoperefund.common.exception.ConflictException;
import com.hoperefund.common.exception.NotFoundException;
import com.hoperefund.income.dto.IncomeResponseDTO;
import com.hoperefund.income.service.IncomeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 소득 API 테스트 (요청 · 응답 · 오류 코드)
 *
 * <p>@WebMvcTest: Controller와 오류 처리기만 띄워서 가짜 HTTP 요청으로 확인 (DB · Service는 안 띄움)
 *
 * <p>@MockitoBean: 진짜 IncomeService 대신 가짜를 넣음 → 응답만 정해 두고 Controller 동작만 본다
 *
 * <p>jsonPath("$.칸") : 응답 JSON의 그 칸 값을 확인
 *
 * <p>[수정 11/11] 응답이 공통 봉투로 바뀌어서 경로 변경: 성공은 $.data.칸 · 실패는 $.error.code
 */
@WebMvcTest(IncomeController.class)
@AutoConfigureMockMvc(addFilters = false)
class IncomeControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private IncomeService incomeService;

    // 필수 칸만 있는 정상 본문 (텍스트 블록 """ """: 여러 줄 문자열)
    private static final String VALID_BODY =
            """
            {"totalSalary": 30000000, "withheldTax": 1000000}
            """;

    // ── GET ──

    @Test
    void 조회_정상이면_200과_소득() throws Exception {
        IncomeResponseDTO income = new IncomeResponseDTO();
        income.setTotalSalary(30_000_000L);
        income.setSmeReductionYn("N");
        when(incomeService.getIncome(1L, 2026)).thenReturn(income);

        mockMvc.perform(get("/api/incomes/2026").header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSalary").value(30_000_000L))
                .andExpect(jsonPath("$.data.smeReductionYn").value("N"));
    }

    @Test
    void 조회_소득이_없으면_404_NOT_FOUND() throws Exception {
        when(incomeService.getIncome(1L, 2026)).thenThrow(new NotFoundException("없음"));

        mockMvc.perform(get("/api/incomes/2026").header("X-Member-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void 조회_회원번호_헤더가_없으면_401() throws Exception {
        mockMvc.perform(get("/api/incomes/2026"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 조회_연도가_숫자가_아니면_400() throws Exception {
        mockMvc.perform(get("/api/incomes/abc").header("X-Member-Id", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"));
    }

    // ── PUT ──

    @Test
    void 저장_정상이면_200과_저장된_소득() throws Exception {
        IncomeResponseDTO saved = new IncomeResponseDTO();
        saved.setTotalSalary(30_000_000L);
        when(incomeService.saveIncome(eq(1L), eq(2026), any())).thenReturn(saved);

        mockMvc.perform(
                        put("/api/incomes/2026")
                                .header("X-Member-Id", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSalary").value(30_000_000L));
    }

    @Test
    void 저장_총급여가_없으면_400이고_Service를_부르지_않는다() throws Exception {
        mockMvc.perform(
                        put("/api/incomes/2026")
                                .header("X-Member-Id", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"withheldTax\": 1000000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"));
        verify(incomeService, never()).saveIncome(anyLong(), anyInt(), any());
    }

    @Test
    void 저장_금액이_음수면_400() throws Exception {
        mockMvc.perform(
                        put("/api/incomes/2026")
                                .header("X-Member-Id", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"totalSalary\": 30000000, \"withheldTax\": 1000000,"
                                                + " \"monthlyRent\": -1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 저장_중소기업_감면이_Y_N이_아니면_400() throws Exception {
        mockMvc.perform(
                        put("/api/incomes/2026")
                                .header("X-Member-Id", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"totalSalary\": 30000000, \"withheldTax\": 1000000,"
                                                + " \"smeReductionYn\": \"X\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 저장_동시에_첫_저장이_겹치면_409() throws Exception {
        when(incomeService.saveIncome(anyLong(), anyInt(), any()))
                .thenThrow(new ConflictException("동시 저장", null));

        mockMvc.perform(
                        put("/api/incomes/2026")
                                .header("X-Member-Id", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }
}
