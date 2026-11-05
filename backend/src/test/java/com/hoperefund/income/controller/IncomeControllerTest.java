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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

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
    void 저장하면_200과_저장한_소득을_돌려준다() throws Exception {
        MockHttpServletResponse res =
                call(
                        put("/api/incomes/2026")
                                .header("X-Member-Id", "81")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY));

        assertEquals(200, res.getStatus());
        assertTrue(text(res).contains("\"totalSalary\":36000000"));
        assertTrue(mapper.rows.containsKey("81-2026"));
    }

    @Test
    void 회원번호_헤더가_없으면_401_UNAUTHORIZED() throws Exception {
        MockHttpServletResponse res =
                call(
                        put("/api/incomes/2026")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY));

        assertEquals(401, res.getStatus());
        assertTrue(text(res).contains("\"error\":\"UNAUTHORIZED\""));
        assertTrue(mapper.rows.isEmpty());
    }

    @Test
    void 총급여가_없으면_400_VALIDATION이고_저장하지_않는다() throws Exception {
        String noSalary = BODY.replace("\"totalSalary\":36000000,", "");
        MockHttpServletResponse res =
                call(
                        put("/api/incomes/2026")
                                .header("X-Member-Id", "81")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(noSalary));

        assertEquals(400, res.getStatus());
        assertTrue(text(res).contains("\"error\":\"VALIDATION\""));
        assertTrue(mapper.rows.isEmpty());
    }

    @Test
    void 음수_금액이면_400_VALIDATION() throws Exception {
        String negative = BODY.replace("\"withheldTax\":1523000", "\"withheldTax\":-1");
        MockHttpServletResponse res =
                call(
                        put("/api/incomes/2026")
                                .header("X-Member-Id", "81")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(negative));

        assertEquals(400, res.getStatus());
        assertTrue(text(res).contains("\"error\":\"VALIDATION\""));
    }

    @Test
    void 연도가_숫자가_아니면_400_VALIDATION() throws Exception {
        MockHttpServletResponse res = call(get("/api/incomes/abc").header("X-Member-Id", "81"));

        assertEquals(400, res.getStatus());
        assertTrue(text(res).contains("\"error\":\"VALIDATION\""));
    }

    @Test
    void 기준값이_없는_연도는_400_NO_CRITERIA() throws Exception {
        MockHttpServletResponse res =
                call(
                        put("/api/incomes/2024")
                                .header("X-Member-Id", "81")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY));

        assertEquals(400, res.getStatus());
        assertTrue(text(res).contains("\"error\":\"NO_CRITERIA\""));
    }

    @Test
    void 저장한_적_없는_소득을_조회하면_404_NOT_FOUND() throws Exception {
        MockHttpServletResponse res = call(get("/api/incomes/2026").header("X-Member-Id", "81"));

        assertEquals(404, res.getStatus());
        assertTrue(text(res).contains("\"error\":\"NOT_FOUND\""));
    }

    @Test
    void 다른_회원의_소득은_조회되지_않는다() throws Exception {
        call(
                put("/api/incomes/2026")
                        .header("X-Member-Id", "81")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY));

        MockHttpServletResponse res = call(get("/api/incomes/2026").header("X-Member-Id", "82"));

        assertEquals(404, res.getStatus());
    }

    @Test
    void 오류_응답에_서버_내부_정보가_없다() throws Exception {
        MockHttpServletResponse res =
                call(
                        put("/api/incomes/2026")
                                .header("X-Member-Id", "81")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{잘못된 JSON"));

        assertEquals(400, res.getStatus());
        String body = text(res);
        assertTrue(body.contains("\"error\":\"VALIDATION\""));
        assertTrue(!body.contains("Exception") && !body.contains("at com."));
    }
}
