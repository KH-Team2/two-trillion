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
    void 계산하면_200과_결과를_돌려주고_헤더의_회원번호와_주소의_연도를_쓴다() throws Exception {
        CalculationController controller =
                new CalculationController(calculationService(this::result));
        MockHttpServletResponse res =
                call(controller, post("/api/calculations/2026").header("X-Member-Id", "81"));

        assertEquals(200, res.getStatus());
        assertTrue(text(res).contains("\"decidedTax\":1471500"));
        assertTrue(text(res).contains("\"expectedRefund\":51500"));
        assertEquals(81L, receivedMemberId);
        assertEquals(2026, receivedTaxYear);
    }

    @Test
    void 계산_회원번호_헤더가_없으면_401이고_계산하지_않는다() throws Exception {
        CalculationController controller =
                new CalculationController(calculationService(this::result));
        MockHttpServletResponse res = call(controller, post("/api/calculations/2026"));

        assertEquals(401, res.getStatus());
        assertTrue(text(res).contains("\"error\":\"UNAUTHORIZED\""));
        assertEquals(0, receivedTaxYear);
    }

    @Test
    void 계산_기준값_없는_연도는_400_NO_CRITERIA() throws Exception {
        CalculationController controller =
                new CalculationController(
                        calculationService(
                                () -> {
                                    throw new NoCriteriaException();
                                }));
        MockHttpServletResponse res =
                call(controller, post("/api/calculations/2024").header("X-Member-Id", "81"));

        assertEquals(400, res.getStatus());
        assertTrue(text(res).contains("\"error\":\"NO_CRITERIA\""));
    }

    @Test
    void 계산_서버가_입력을_거부하면_400_CALC_REJECTED() throws Exception {
        CalculationController controller =
                new CalculationController(
                        calculationService(
                                () -> {
                                    throw new CalcRejectedException("계산 서버 입력 거부 /calculate", null);
                                }));
        MockHttpServletResponse res =
                call(controller, post("/api/calculations/2026").header("X-Member-Id", "81"));

        assertEquals(400, res.getStatus());
        assertTrue(text(res).contains("\"error\":\"CALC_REJECTED\""));
    }

    @Test
    void 계산_서버에_연결할_수_없으면_503_CALC_UNAVAILABLE이고_내부_원인은_응답에_없다() throws Exception {
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
        assertTrue(text(res).contains("\"error\":\"CALC_UNAVAILABLE\""));
        assertFalse(text(res).contains("내부주소")); // 원인은 서버 로그에만
    }

    @Test
    void 소득이_없으면_404_NOT_FOUND() throws Exception {
        CalculationController controller =
                new CalculationController(
                        calculationService(
                                () -> {
                                    throw new NotFoundException("2026년 소득 정보가 아직 없어요");
                                }));
        MockHttpServletResponse res =
                call(controller, post("/api/calculations/2026").header("X-Member-Id", "81"));

        assertEquals(404, res.getStatus());
        assertTrue(text(res).contains("\"error\":\"NOT_FOUND\""));
    }

    @Test
    void 예상하지_못한_오류는_500_SERVER_ERROR이고_내부_정보가_없다() throws Exception {
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
        assertTrue(text(res).contains("\"error\":\"SERVER_ERROR\""));
        assertFalse(text(res).contains("ORA-"));
        assertFalse(text(res).contains("SELECT"));
    }

    @Test
    void 계산은_POST만_받는다() throws Exception {
        CalculationController controller =
                new CalculationController(calculationService(this::result));
        MockHttpServletResponse res =
                call(controller, get("/api/calculations/2026").header("X-Member-Id", "81"));

        assertEquals(405, res.getStatus());
        assertTrue(text(res).contains("\"error\":\"METHOD_NOT_ALLOWED\""));
    }

    // ---------- 혜택 진단 GET /api/benefits/{taxYear} ----------

    @Test
    void 혜택_진단은_해당_비해당_정보부족을_그대로_돌려준다() throws Exception {
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
    void 혜택_진단_회원번호가_숫자가_아니면_400_VALIDATION() throws Exception {
        BenefitController controller =
                new BenefitController(benefitService(BenefitResponseDTO::new));
        MockHttpServletResponse res =
                call(controller, get("/api/benefits/2026").header("X-Member-Id", "1 OR 1=1"));

        assertEquals(400, res.getStatus());
        assertEquals(0L, receivedMemberId);
    }
}
