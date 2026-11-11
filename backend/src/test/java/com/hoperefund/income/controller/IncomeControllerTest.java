package com.hoperefund.income.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.hoperefund.common.exception.GlobalExceptionHandler;
import com.hoperefund.income.dto.IncomeRequestDTO;
import com.hoperefund.income.dto.IncomeResponseDTO;
import com.hoperefund.income.mapper.IncomeMapper;
import com.hoperefund.income.service.IncomeService;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 소득 API의 요청 · 응답 모양을 확인해요 (서버 · DB 없이 MockMvc). 화면이 받는 상태 코드와 {"error", "message"} 모양이
 * api-rules.md와 같은지 봐요.
 */
class IncomeControllerTest {

    /** 메모리에만 저장하는 가짜 IncomeMapper (기준값은 2025 · 2026년만) */
    static class FakeIncomeMapper implements IncomeMapper {
        final Map<String, IncomeResponseDTO> rows = new HashMap<>();

        @Override
        public IncomeResponseDTO findByMemberAndYear(long memberId, int taxYear) {
            return rows.get(memberId + "-" + taxYear);
        }

        @Override
        public int upsert(long memberId, int taxYear, IncomeRequestDTO req) {
            IncomeResponseDTO r = new IncomeResponseDTO();
            r.setTaxYear(taxYear);
            r.setTotalSalary(req.getTotalSalary());
            r.setWithheldTax(req.getWithheldTax());
            rows.put(memberId + "-" + taxYear, r);
            return 1;
        }

        @Override
        public int countCriteria(int taxYear) {
            return taxYear == 2025 || taxYear == 2026 ? 1 : 0;
        }
    }

    private static final String BODY =
            """
            {"totalSalary":36000000,"withheldTax":1523000,"nationalPension":0,"healthInsurance":0,
             "dependents":0,"children":0,"monthlyRent":0,"housingSaving":0,"pensionSaving":0,
             "irpSaving":0,"smeReductionYn":"N"}
            """;

    private FakeIncomeMapper mapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mapper = new FakeIncomeMapper();
        mockMvc =
                MockMvcBuilders.standaloneSetup(new IncomeController(new IncomeService(mapper)))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    private MockHttpServletResponse call(RequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private String text(MockHttpServletResponse response) throws Exception {
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("저장하면 200과 저장한 소득을 돌려준다")
    void savesAndReturnsIncome() throws Exception {
        MockHttpServletResponse res =
                call(
                        put("/api/incomes/2026")
                                .header("X-Member-Id", "81")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY));

        assertEquals(200, res.getStatus());
        assertTrue(text(res).contains("\"success\":true"));
        assertTrue(text(res).contains("\"data\":{"));
        assertTrue(text(res).contains("\"totalSalary\":36000000"));
        assertTrue(mapper.rows.containsKey("81-2026"));
    }

    @Test
    @DisplayName("회원번호 헤더가 없으면 401 UNAUTHORIZED")
    void returns401WithoutMemberHeader() throws Exception {
        MockHttpServletResponse res =
                call(
                        put("/api/incomes/2026")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY));

        assertEquals(401, res.getStatus());
        assertTrue(text(res).contains("\"code\":\"UNAUTHORIZED\""));
        assertTrue(mapper.rows.isEmpty());
    }

    @Test
    @DisplayName("총급여가 없으면 400 VALIDATION이고 저장하지 않는다")
    void returns400WhenTotalSalaryMissing() throws Exception {
        String noSalary = BODY.replace("\"totalSalary\":36000000,", "");
        MockHttpServletResponse res =
                call(
                        put("/api/incomes/2026")
                                .header("X-Member-Id", "81")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(noSalary));

        assertEquals(400, res.getStatus());
        assertTrue(text(res).contains("\"code\":\"VALIDATION\""));
        assertTrue(mapper.rows.isEmpty());
    }

    @Test
    @DisplayName("음수 금액이면 400 VALIDATION")
    void returns400WhenAmountNegative() throws Exception {
        String negative = BODY.replace("\"withheldTax\":1523000", "\"withheldTax\":-1");
        MockHttpServletResponse res =
                call(
                        put("/api/incomes/2026")
                                .header("X-Member-Id", "81")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(negative));

        assertEquals(400, res.getStatus());
        assertTrue(text(res).contains("\"code\":\"VALIDATION\""));
    }

    @Test
    @DisplayName("총급여가 10억을 넘으면 400 VALIDATION이고 저장하지 않는다")
    void returns400WhenTotalSalaryOverLimit() throws Exception {
        // 0 하나 더 붙인 오타: 3,600만 → 36억
        String tooBig = BODY.replace("\"totalSalary\":36000000", "\"totalSalary\":3600000000");
        MockHttpServletResponse res =
                call(
                        put("/api/incomes/2026")
                                .header("X-Member-Id", "81")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(tooBig));

        assertEquals(400, res.getStatus());
        assertTrue(text(res).contains("\"code\":\"VALIDATION\""));
        assertTrue(text(res).contains("총급여가 너무 커요"));
        assertTrue(mapper.rows.isEmpty());
    }

    @Test
    @DisplayName("총급여가 딱 10억이면 저장된다")
    void savesWhenTotalSalaryAtLimit() throws Exception {
        String limit = BODY.replace("\"totalSalary\":36000000", "\"totalSalary\":1000000000");
        MockHttpServletResponse res =
                call(
                        put("/api/incomes/2026")
                                .header("X-Member-Id", "81")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(limit));

        assertEquals(200, res.getStatus());
    }

    @Test
    @DisplayName("연금저축이 1억을 넘으면 400 VALIDATION")
    void returns400WhenPensionSavingOverLimit() throws Exception {
        String tooBig = BODY.replace("\"pensionSaving\":0", "\"pensionSaving\":100000001");
        MockHttpServletResponse res =
                call(
                        put("/api/incomes/2026")
                                .header("X-Member-Id", "81")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(tooBig));

        assertEquals(400, res.getStatus());
        assertTrue(text(res).contains("\"code\":\"VALIDATION\""));
        assertTrue(mapper.rows.isEmpty());
    }

    @Test
    @DisplayName("JSON이 아닌 형식으로 보내면 500이 아니라 415이고 저장하지 않는다")
    void returns415WhenBodyIsNotJson() throws Exception {
        // 보안 점검 4-1: 전에는 처리기가 없어 맨 아래 Exception 처리기(500 SERVER_ERROR)로 떨어졌음
        MockHttpServletResponse res =
                call(
                        put("/api/incomes/2026")
                                .header("X-Member-Id", "81")
                                .contentType(MediaType.TEXT_PLAIN)
                                .content(BODY));

        assertEquals(415, res.getStatus());
        assertTrue(text(res).contains("\"code\":\"UNSUPPORTED_MEDIA_TYPE\""));
        assertTrue(!text(res).contains("errorId")); // 사용자 쪽 오류라 추적 번호 없음
        assertTrue(mapper.rows.isEmpty());
    }

    @Test
    @DisplayName("연도가 숫자가 아니면 400 VALIDATION")
    void returns400WhenYearIsNotNumber() throws Exception {
        MockHttpServletResponse res = call(get("/api/incomes/abc").header("X-Member-Id", "81"));

        assertEquals(400, res.getStatus());
        assertTrue(text(res).contains("\"code\":\"VALIDATION\""));
    }

    @Test
    @DisplayName("기준값이 없는 연도는 400 NO CRITERIA")
    void returns400NoCriteriaForUnknownYear() throws Exception {
        MockHttpServletResponse res =
                call(
                        put("/api/incomes/2024")
                                .header("X-Member-Id", "81")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY));

        assertEquals(400, res.getStatus());
        assertTrue(text(res).contains("\"code\":\"NO_CRITERIA\""));
    }

    @Test
    @DisplayName("저장한 적 없는 소득을 조회하면 404 NOT FOUND")
    void returns404WhenIncomeMissing() throws Exception {
        MockHttpServletResponse res = call(get("/api/incomes/2026").header("X-Member-Id", "81"));

        assertEquals(404, res.getStatus());
        assertTrue(text(res).contains("\"code\":\"NOT_FOUND\""));
    }

    @Test
    @DisplayName("다른 회원의 소득은 조회되지 않는다")
    void doesNotReturnOtherMembersIncome() throws Exception {
        call(
                put("/api/incomes/2026")
                        .header("X-Member-Id", "81")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY));

        MockHttpServletResponse res = call(get("/api/incomes/2026").header("X-Member-Id", "82"));

        assertEquals(404, res.getStatus());
    }

    @Test
    @DisplayName("오류 응답에 서버 내부 정보가 없다")
    void errorResponseHidesInternalDetails() throws Exception {
        MockHttpServletResponse res =
                call(
                        put("/api/incomes/2026")
                                .header("X-Member-Id", "81")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{잘못된 JSON"));

        assertEquals(400, res.getStatus());
        String body = text(res);
        assertTrue(body.contains("\"code\":\"VALIDATION\""));
        assertTrue(!body.contains("Exception") && !body.contains("at com."));
    }
}
