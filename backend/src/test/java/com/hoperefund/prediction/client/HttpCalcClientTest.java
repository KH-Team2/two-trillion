package com.hoperefund.prediction.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hoperefund.common.exception.CalcRejectedException;
import com.hoperefund.common.exception.CalcUnavailableException;
import com.hoperefund.common.exception.NoCriteriaException;
import com.hoperefund.income.dto.IncomeResponseDTO;
import com.hoperefund.prediction.dto.CalculationResponseDTO;
import com.hoperefund.prediction.dto.CardSpendDTO;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 계산 서버 호출(HttpCalcClient)을 진짜 파이썬 서버 없이 확인해요. JDK에 들어 있는 작은 HTTP 서버(HttpServer)를 테스트 안에서 띄워 계산 서버인
 * 척 응답해요.
 */
class HttpCalcClientTest {

    private static final String TOKEN = "test-token";

    private HttpServer server;
    // 가짜 서버가 받은 요청 (주소 · 토큰 헤더 · 본문)
    private String receivedPath;
    private String receivedToken;
    private String receivedBody;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** 가짜 계산 서버를 띄워요: 어떤 요청이 와도 status · body로 응답 (body가 ""이면 빈 응답) */
    private String startServer(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(
                "/",
                exchange -> {
                    receivedPath = exchange.getRequestURI().getPath();
                    receivedToken = exchange.getRequestHeaders().getFirst("X-Calc-Token");
                    receivedBody =
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8);
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    if (bytes.length == 0) {
                        exchange.sendResponseHeaders(status, -1); // -1 = 본문 없음
                    } else {
                        exchange.sendResponseHeaders(status, bytes.length);
                        try (OutputStream out = exchange.getResponseBody()) {
                            out.write(bytes);
                        }
                    }
                    exchange.close();
                });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private IncomeResponseDTO income() {
        IncomeResponseDTO income = new IncomeResponseDTO();
        income.setTaxYear(2026);
        income.setTotalSalary(36_000_000L);
        income.setWithheldTax(1_523_000L);
        income.setNationalPension(0L);
        income.setHealthInsurance(0L);
        income.setDependents(0);
        income.setChildren(0);
        income.setMonthlyRent(0L);
        income.setHousingSaving(0L);
        income.setPensionSaving(0L);
        income.setIrpSaving(0L);
        income.setSmeReductionYn("N");
        return income;
    }

    private CalculationResponseDTO calculate(String baseUrl) {
        HttpCalcClient client = new HttpCalcClient(baseUrl, TOKEN);
        return client.calculate(
                2026, income(), "N", List.of(new CardSpendDTO(1L, "CREDIT", 12_000_000L)));
    }

    @Test
    void 계산_요청에_토큰과_입력값을_보내고_결과를_받는다() throws IOException {
        String url =
                startServer(
                        200,
"""
{"taxYear":2026,"totalSalary":36000000,"withheldTax":1523000,"taxableIncome":23850000,
 "calculatedTax":2317500,"decidedTax":1471500,"expectedRefund":51500,
 "details":[{"typeCode":"EARNED_DEDUCTION","typeName":"근로소득공제","kind":"INCOME_DEDUCTION",
             "baseAmount":36000000,"deductionAmount":10650000,"sourceNote":"신고안내"}]}
""");

        CalculationResponseDTO result = calculate(url);

        assertEquals("/calculate", receivedPath);
        assertEquals(TOKEN, receivedToken);
        assertTrue(receivedBody.contains("\"totalSalary\":36000000"));
        assertTrue(receivedBody.contains("\"homelessHeadYn\":\"N\""));
        assertTrue(receivedBody.contains("\"payMethod\":\"CREDIT\""));
        assertEquals(1_471_500L, result.getDecidedTax());
        assertEquals(51_500L, result.getExpectedRefund());
        assertEquals("EARNED_DEDUCTION", result.getDetails().get(0).getTypeCode());
    }

    @Test
    void 기준값이_없는_연도면_NO_CRITERIA로_바꾼다() throws IOException {
        String url = startServer(400, "{\"error\":\"NO_CRITERIA\",\"message\":\"기준값이 없어요\"}");
        assertThrows(NoCriteriaException.class, () -> calculate(url));
    }

    @Test
    void 계산_서버가_입력값을_거부하면_CALC_REJECTED() throws IOException {
        String url = startServer(400, "{\"error\":\"VALIDATION\",\"message\":\"입력값 오류\"}");
        assertThrows(CalcRejectedException.class, () -> calculate(url));
    }

    @Test
    void 토큰이_틀리면_사용자가_고칠_수_없는_오류라_CALC_UNAVAILABLE() throws IOException {
        String url = startServer(401, "{\"error\":\"UNAUTHORIZED\",\"message\":\"토큰 확인\"}");
        assertThrows(CalcUnavailableException.class, () -> calculate(url));
    }

    @Test
    void 계산_서버가_꺼져_있으면_CALC_UNAVAILABLE() throws IOException {
        String url = startServer(200, "{}");
        server.stop(0); // 주소는 받아 두고 서버를 꺼요 → 연결 거부
        server = null;
        assertThrows(CalcUnavailableException.class, () -> calculate(url));
    }

    @Test
    void 계산_서버가_빈_응답을_주면_CALC_UNAVAILABLE() throws IOException {
        String url = startServer(200, "");
        assertThrows(CalcUnavailableException.class, () -> calculate(url));
    }
}
