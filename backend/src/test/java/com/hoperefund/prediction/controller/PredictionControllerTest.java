package com.hoperefund.prediction.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.hoperefund.common.exception.CalcRejectedException;
import com.hoperefund.common.exception.CalcUnavailableException;
import com.hoperefund.common.exception.GlobalExceptionHandler;
import com.hoperefund.common.exception.NoCriteriaException;
import com.hoperefund.common.exception.NotFoundException;
import com.hoperefund.prediction.dto.BenefitResponseDTO;
import com.hoperefund.prediction.dto.BenefitResultDTO;
import com.hoperefund.prediction.dto.CalculationResponseDTO;
import com.hoperefund.prediction.service.BenefitService;
import com.hoperefund.prediction.service.CalculationService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 계산 · 혜택 진단 API의 요청 · 응답 모양을 확인해요 (서버 · DB · 계산 서버 없이 MockMvc). 서비스는 정해 둔 결과나 오류만 돌려주는 가짜로 바꿔서,
 * 컨트롤러와 오류 처리(GlobalExceptionHandler)만 봐요.
 */
class PredictionControllerTest {

    // 가짜 서비스가 마지막으로 받은 회원번호 · 연도
    private long receivedMemberId;
    private int receivedTaxYear;

    /** 정해 둔 결과(또는 오류)를 돌려주는 가짜 계산 서비스 */
    private CalculationService calculationService(Supplier<CalculationResponseDTO> answer) {
        return new CalculationService(null, null, null, null, false, "mock") {
            @Override
            public CalculationResponseDTO calculate(long memberId, int taxYear) {
                receivedMemberId = memberId;
                receivedTaxYear = taxYear;
                return answer.get();
            }
        };
    }

    /** 정해 둔 결과(또는 오류)를 돌려주는 가짜 혜택 진단 서비스 */
    private BenefitService benefitService(Supplier<BenefitResponseDTO> answer) {
        return new BenefitService(null, null, null) {
            @Override
            public BenefitResponseDTO diagnose(long memberId, int taxYear) {
                receivedMemberId = memberId;
                receivedTaxYear = taxYear;
                return answer.get();
            }
        };
    }

    private MockHttpServletResponse call(Object controller, RequestBuilder request)
            throws Exception {
        MockMvc mockMvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private String text(MockHttpServletResponse response) throws Exception {
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    private CalculationResponseDTO result() {
        CalculationResponseDTO r = new CalculationResponseDTO();
        r.setTaxYear(2026);
        r.setDecidedTax(1_471_500L);
        r.setExpectedRefund(51_500L);
        return r;
    }

    // ---------- 계산 POST /api/calculations/{taxYear} ----------

    @Test
    @DisplayName("계산하면 200과 결과를 돌려주고 헤더의 회원번호와 주소의 연도를 쓴다")
    void calculateReturns200AndUsesHeaderMemberAndPathYear() throws Exception {
        CalculationController controller =
                new CalculationController(calculationService(this::result));
        MockHttpServletResponse res =
                call(controller, post("/api/calculations/2026").header("X-Member-Id", "81"));

        assertEquals(200, res.getStatus());
        // 공통 봉투: 결과는 data 안에
        assertTrue(text(res).startsWith("{\"success\":true,\"data\":{"));
        assertTrue(text(res).contains("\"error\":null"));
        assertTrue(text(res).contains("\"decidedTax\":1471500"));
        assertTrue(text(res).contains("\"expectedRefund\":51500"));
        assertEquals(81L, receivedMemberId);
        assertEquals(2026, receivedTaxYear);
    }

    @Test
    @DisplayName("계산 회원번호 헤더가 없으면 401이고 계산하지 않는다")
    void calculateReturns401WithoutMemberHeader() throws Exception {
        CalculationController controller =
                new CalculationController(calculationService(this::result));
        MockHttpServletResponse res = call(controller, post("/api/calculations/2026"));

        assertEquals(401, res.getStatus());
        assertTrue(text(res).contains("\"code\":\"UNAUTHORIZED\""));
        assertEquals(0, receivedTaxYear);
    }

    @Test
    @DisplayName("계산 기준값 없는 연도는 400 NO CRITERIA")
    void calculateReturns400NoCriteriaForUnknownYear() throws Exception {
        CalculationController controller =
                new CalculationController(
                        calculationService(
                                () -> {
                                    throw new NoCriteriaException();
                                }));
        MockHttpServletResponse res =
                call(controller, post("/api/calculations/2024").header("X-Member-Id", "81"));

        assertEquals(400, res.getStatus());
        assertTrue(text(res).contains("\"code\":\"NO_CRITERIA\""));
    }

    @Test
    @DisplayName("계산 서버가 입력을 거부하면 400 CALC REJECTED")
    void calculateReturns400WhenCalcServerRejects() throws Exception {
        CalculationController controller =
                new CalculationController(
                        calculationService(
                                () -> {
                                    throw new CalcRejectedException("계산 서버 입력 거부 /calculate", null);
                                }));
        MockHttpServletResponse res =
                call(controller, post("/api/calculations/2026").header("X-Member-Id", "81"));

        assertEquals(400, res.getStatus());
        assertTrue(text(res).contains("\"code\":\"CALC_REJECTED\""));
    }

    @Test
    @DisplayName("계산 서버에 연결할 수 없으면 503 CALC UNAVAILABLE이고 내부 원인은 응답에 없다")
    void calculateReturns503AndHidesCauseWhenCalcServerDown() throws Exception {
        CalculationController controller =
                new CalculationController(
                        calculationService(
                                () -> {
                                    throw new CalcUnavailableException(
                                            "계산 서버 연결 실패 /calculate http://내부주소", null);
                                }));
        MockHttpServletResponse res =
                call(controller, post("/api/calculations/2026").header("X-Member-Id", "81"));

        assertEquals(503, res.getStatus());
        assertTrue(text(res).contains("\"code\":\"CALC_UNAVAILABLE\""));
        assertTrue(text(res).matches(".*\"errorId\":\"[0-9a-f]{8}\".*")); // 503은 추적 번호 있음
        assertFalse(text(res).contains("내부주소")); // 원인은 서버 로그에만
    }

    @Test
    @DisplayName("소득이 없으면 404 NOT FOUND")
    void calculateReturns404WhenIncomeMissing() throws Exception {
        CalculationController controller =
                new CalculationController(
                        calculationService(
                                () -> {
                                    throw new NotFoundException("2026년 소득 정보가 아직 없어요");
                                }));
        MockHttpServletResponse res =
                call(controller, post("/api/calculations/2026").header("X-Member-Id", "81"));

        assertEquals(404, res.getStatus());
        assertTrue(text(res).contains("\"code\":\"NOT_FOUND\""));
        assertFalse(text(res).contains("errorId")); // 사용자 쪽 오류엔 추적 번호 없음
    }

    @Test
    @DisplayName("예상하지 못한 오류는 500 SERVER ERROR이고 내부 정보가 없다")
    void unexpectedErrorReturns500AndHidesInternalDetails() throws Exception {
        CalculationController controller =
                new CalculationController(
                        calculationService(
                                () -> {
                                    throw new IllegalStateException(
                                            "ORA-00942 SELECT * FROM PREDICTION");
                                }));
        MockHttpServletResponse res =
                call(controller, post("/api/calculations/2026").header("X-Member-Id", "81"));

        assertEquals(500, res.getStatus());
        assertTrue(text(res).contains("\"code\":\"SERVER_ERROR\""));
        assertTrue(text(res).matches(".*\"errorId\":\"[0-9a-f]{8}\".*")); // 500은 추적 번호 있음
        assertFalse(text(res).contains("ORA-"));
        assertFalse(text(res).contains("SELECT"));
    }

    @Test
    @DisplayName("계산은 POST만 받는다")
    void calculateAcceptsOnlyPost() throws Exception {
        CalculationController controller =
                new CalculationController(calculationService(this::result));
        MockHttpServletResponse res =
                call(controller, get("/api/calculations/2026").header("X-Member-Id", "81"));

        assertEquals(405, res.getStatus());
        assertTrue(text(res).contains("\"code\":\"METHOD_NOT_ALLOWED\""));
    }

    // ---------- 혜택 진단 GET /api/benefits/{taxYear} ----------

    @Test
    @DisplayName("혜택 진단은 해당 비해당 정보부족을 그대로 돌려준다")
    void benefitReturnsEligibleValuesAsIs() throws Exception {
        BenefitController controller =
                new BenefitController(
                        benefitService(
                                () -> {
                                    BenefitResultDTO sme = new BenefitResultDTO();
                                    sme.setBenefitCode("SME_YOUTH");
                                    sme.setEligible(null); // 정보 부족
                                    BenefitResultDTO rent = new BenefitResultDTO();
                                    rent.setBenefitCode("RENT");
                                    rent.setEligible(false);
                                    BenefitResponseDTO r = new BenefitResponseDTO();
                                    r.setTaxYear(2026);
                                    r.setResults(List.of(sme, rent));
                                    return r;
                                }));
        MockHttpServletResponse res =
                call(controller, get("/api/benefits/2026").header("X-Member-Id", "81"));

        assertEquals(200, res.getStatus());
        String body = text(res);
        assertTrue(body.contains("\"benefitCode\":\"SME_YOUTH\""));
        assertTrue(body.contains("\"eligible\":null"));
        assertTrue(body.contains("\"eligible\":false"));
        assertEquals(81L, receivedMemberId);
    }

    @Test
    @DisplayName("혜택 진단 회원번호가 숫자가 아니면 400 VALIDATION")
    void benefitReturns400WhenMemberIdIsNotNumber() throws Exception {
        BenefitController controller =
                new BenefitController(benefitService(BenefitResponseDTO::new));
        MockHttpServletResponse res =
                call(controller, get("/api/benefits/2026").header("X-Member-Id", "1 OR 1=1"));

        assertEquals(400, res.getStatus());
        assertEquals(0L, receivedMemberId);
    }
}
