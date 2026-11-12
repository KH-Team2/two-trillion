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
import org.junit.jupiter.api.DisplayName;
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
 *
 * <p>[수정 11/12] 테스트 메서드 이름을 영문으로 · 이유: 팀 테스트 이름 규칙에 맞춤 (팀원 리뷰 · PR #7과 같은 방식)
 * → 원래 한글 설명은 @DisplayName에 그대로 남김 (테스트 결과 화면에 이 글자가 보임)
 *
 * <p>[수정 11/12] 입력 오류의 한글 문구($.error.message) · 응답의 incomeId · taxYear 확인 추가
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
    @DisplayName("조회: 정상이면 200과 소득 (incomeId · taxYear 포함)")
    void getReturns200WithIncome() throws Exception {
        IncomeResponseDTO income = new IncomeResponseDTO();
        income.setIncomeId(5L); // [수정 11/12] 추가
        income.setTaxYear(2026); // [수정 11/12] 추가
        income.setTotalSalary(30_000_000L);
        income.setSmeReductionYn("N");
        when(incomeService.getIncome(1L, 2026)).thenReturn(income);

        mockMvc.perform(get("/api/incomes/2026").header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.incomeId").value(5)) // [수정 11/12] 추가
                .andExpect(jsonPath("$.data.taxYear").value(2026)) // [수정 11/12] 추가
                .andExpect(jsonPath("$.data.totalSalary").value(30_000_000L))
                .andExpect(jsonPath("$.data.smeReductionYn").value("N"));
    }

    @Test
    @DisplayName("조회: 소득이 없으면 404 NOT_FOUND")
    void getReturns404WhenIncomeMissing() throws Exception {
        when(incomeService.getIncome(1L, 2026)).thenThrow(new NotFoundException("없음"));

        mockMvc.perform(get("/api/incomes/2026").header("X-Member-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("조회: 회원번호 헤더가 없으면 401")
    void getReturns401WhenMemberHeaderMissing() throws Exception {
        mockMvc.perform(get("/api/incomes/2026"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("조회: 연도가 숫자가 아니면 400")
    void getReturns400WhenTaxYearNotNumber() throws Exception {
        mockMvc.perform(get("/api/incomes/abc").header("X-Member-Id", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"));
    }

    // ── PUT ──

    @Test
    @DisplayName("저장: 정상이면 200과 저장된 소득")
    void putReturns200WithSavedIncome() throws Exception {
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
    @DisplayName("저장: 총급여가 없으면 400 · 한글 문구 · Service를 부르지 않음")
    void putReturns400WithMessageWhenTotalSalaryMissing() throws Exception {
        mockMvc.perform(
                        put("/api/incomes/2026")
                                .header("X-Member-Id", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"withheldTax\": 1000000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"))
                // [수정 11/12] 추가: DTO에 적은 한글 문구가 그대로 화면으로 가는지
                .andExpect(jsonPath("$.error.message").value("총급여를 입력해 주세요"));
        verify(incomeService, never()).saveIncome(anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("저장: 금액이 음수면 400 · 한글 문구")
    void putReturns400WhenAmountNegative() throws Exception {
        mockMvc.perform(
                        put("/api/incomes/2026")
                                .header("X-Member-Id", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"totalSalary\": 30000000, \"withheldTax\": 1000000,"
                                                + " \"monthlyRent\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("월세는 0원 이상이어야 해요")); // [수정 11/12] 추가
    }

    @Test
    @DisplayName("저장: 중소기업 감면이 Y/N이 아니면 400 · 한글 문구")
    void putReturns400WhenSmeReductionYnInvalid() throws Exception {
        mockMvc.perform(
                        put("/api/incomes/2026")
                                .header("X-Member-Id", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"totalSalary\": 30000000, \"withheldTax\": 1000000,"
                                                + " \"smeReductionYn\": \"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect( // [수정 11/12] 추가
                        jsonPath("$.error.message").value("중소기업 감면 신청 여부는 Y 또는 N이에요"));
    }

    @Test
    @DisplayName("저장: 동시에 첫 저장이 겹치면 409")
    void putReturns409WhenConcurrentFirstSave() throws Exception {
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
