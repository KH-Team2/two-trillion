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
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("계산 요청에 토큰과 입력값을 보내고 결과를 받는다")
    void sendsTokenAndInputsAndReceivesResult() throws IOException {
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
    @DisplayName("기준값이 없는 연도면 NO CRITERIA로 바꾼다")
    void mapsNoCriteriaResponse() throws IOException {
        String url = startServer(400, "{\"error\":\"NO_CRITERIA\",\"message\":\"기준값이 없어요\"}");
        assertThrows(NoCriteriaException.class, () -> calculate(url));
    }

    @Test
    @DisplayName("계산 서버가 입력값을 거부하면 CALC REJECTED")
    void throwsCalcRejectedWhenServerRejectsInput() throws IOException {
        String url = startServer(400, "{\"error\":\"VALIDATION\",\"message\":\"입력값 오류\"}");
        assertThrows(CalcRejectedException.class, () -> calculate(url));
    }

    @Test
    @DisplayName("http 모드인데 토큰이 비어 있으면 서버가 켜지지 않는다")
    void failsToStartWhenTokenBlankInHttpMode() {
        // 전에는 정상으로 켜지고, 계산할 때 처음으로 503이 나서 설정 누락을 늦게 알았음
        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () -> new HttpCalcClient("http://127.0.0.1:1", ""));
        assertTrue(e.getMessage().contains("CALC_API_TOKEN"));
        assertThrows(
                IllegalStateException.class, () -> new HttpCalcClient("http://127.0.0.1:1", "  "));
    }

    @Test
    @DisplayName("토큰이 틀리면 사용자가 고칠 수 없는 오류라 CALC UNAVAILABLE")
    void throwsCalcUnavailableWhenTokenWrong() throws IOException {
        String url = startServer(401, "{\"error\":\"UNAUTHORIZED\",\"message\":\"토큰 확인\"}");
        assertThrows(CalcUnavailableException.class, () -> calculate(url));
    }

    @Test
    @DisplayName("계산 서버가 꺼져 있으면 CALC UNAVAILABLE")
    void throwsCalcUnavailableWhenServerDown() throws IOException {
        String url = startServer(200, "{}");
        server.stop(0); // 주소는 받아 두고 서버를 꺼요 → 연결 거부
        server = null;
        assertThrows(CalcUnavailableException.class, () -> calculate(url));
    }

    @Test
    @DisplayName("계산 서버가 빈 응답을 주면 CALC UNAVAILABLE")
    void throwsCalcUnavailableWhenResponseEmpty() throws IOException {
        String url = startServer(200, "");
        assertThrows(CalcUnavailableException.class, () -> calculate(url));
    }
}
