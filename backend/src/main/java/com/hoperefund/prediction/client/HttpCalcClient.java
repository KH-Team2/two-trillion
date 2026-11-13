package com.hoperefund.prediction.client;

import com.hoperefund.common.exception.CalcRejectedException;
import com.hoperefund.common.exception.CalcUnavailableException;
import com.hoperefund.common.exception.NoCriteriaException;
import com.hoperefund.income.dto.IncomeResponseDTO;
import com.hoperefund.prediction.dto.BenefitRequestDTO;
import com.hoperefund.prediction.dto.BenefitResponseDTO;
import com.hoperefund.prediction.dto.CalcRequestDTO;
import com.hoperefund.prediction.dto.CalculationResponseDTO;
import com.hoperefund.prediction.dto.CardSpendDTO;
import com.hoperefund.prediction.dto.MemberQualificationDTO;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 파이썬 계산 서버를 HTTP로 불러요 (calc.mode=http 일 때만 Bean 등록) - POST {CALC_BASE_URL}/calculate · /benefit
 * (JSON 이름은 camelCase · 「동석님 전달」과 같음) - 헤더 X-Calc-Token: 계산 서버의 CALC_API_TOKEN과 같은 값 (.env) - 계산
 * 서버가 NO_CRITERIA를 주면 그대로 NO_CRITERIA · 입력값을 거부(400)하면 CALC_REJECTED - 토큰이 틀렸거나(401) 연결이 안 되거나 빈
 * 응답이면 503 안내 (설정 · 서버 문제라 원인은 로그에만)
 */
@Component
@ConditionalOnProperty(name = "calc.mode", havingValue = "http")
public class HttpCalcClient implements CalcClient {

    private final RestClient restClient;

    /** 테스트용: 기본 제한 시간(연결 3초 · 응답 10초)으로 만들어요 */
    public HttpCalcClient(String baseUrl, String token) {
        this(baseUrl, token, Duration.ofSeconds(3), Duration.ofSeconds(10));
    }

    // application.yml의 calc.* 설정을 읽어요 (base-url · api-token · connect-timeout · read-timeout)
    @Autowired
    public HttpCalcClient(
            @Value("${calc.base-url}") String baseUrl,
            @Value("${calc.api-token:}") String token,
            @Value("${calc.connect-timeout:3s}") Duration connectTimeout,
            @Value("${calc.read-timeout:10s}") Duration readTimeout) {
        // http 모드인데 토큰이 없으면 계산 서버가 모든 요청을 막아요(503)
        // → 서버를 켜는 순간 바로 알려서 설정 누락을 늦게 알지 않게
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "calc.mode=http인데 CALC_API_TOKEN이 비어 있어요."
                            + " backend/.env에 계산 서버(calc/.env)와 같은 값을 넣어 주세요");
        }
        // 연결 · 응답 제한 시간: 계산 서버 주소가 틀리거나 멈춰도 요청이 무한정 기다리지 않게
        // HTTP/1.1 고정: 계산 서버(uvicorn)가 HTTP/2 전환 요청을 거절해 남기던 경고(Unsupported upgrade request) 없애기
        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(connectTimeout)
                        .version(HttpClient.Version.HTTP_1_1)
                        .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl).requestFactory(factory);
        builder.defaultHeader("X-Calc-Token", token);
        this.restClient = builder.build();
    }

    @Override
    public CalculationResponseDTO calculate(
            int taxYear,
            IncomeResponseDTO income,
            String homelessHeadYn,
            List<CardSpendDTO> cardSpends) {
        // 계산 서버가 받는 칸만 골라 보내요 (그 서버는 모르는 칸이 오면 400으로 막아요)
        CalcRequestDTO body =
                new CalcRequestDTO(
                        taxYear,
                        income.getTotalSalary(),
                        income.getWithheldTax(),
                        income.getNationalPension(),
                        income.getHealthInsurance(),
                        income.getDependents(),
                        income.getChildren(),
                        income.getMonthlyRent(),
                        income.getHousingSaving(),
                        income.getPensionSaving(),
                        income.getIrpSaving(),
                        income.getSmeReductionYn(),
                        homelessHeadYn,
                        cardSpends);
        return post("/calculate", body, CalculationResponseDTO.class);
    }

    @Override
    public BenefitResponseDTO diagnose(
            int taxYear, long totalSalary, MemberQualificationDTO member) {
        // 날짜는 "2024-03-02" 모양의 글자로 보내요 (값이 없으면 null)
        String birthDate = member.getBirthDate() == null ? null : member.getBirthDate().toString();
        String hireDate = member.getHireDate() == null ? null : member.getHireDate().toString();
        // 군 복무 개월은 값이 없으면 0
        int militaryMonths = Objects.requireNonNullElse(member.getMilitaryMonths(), 0);
        BenefitRequestDTO body =
                new BenefitRequestDTO(
                        taxYear,
                        totalSalary,
                        birthDate,
                        militaryMonths,
                        hireDate,
                        member.getCompanySize(),
                        member.getHomelessHeadYn());
        return post("/benefit", body, BenefitResponseDTO.class);
    }

    private <T> T post(String path, Object body, Class<T> type) {
        T result;
        try {
            result =
                    restClient
                            .post()
                            .uri(path)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(type);
        } catch (HttpClientErrorException e) {
            if (e.getResponseBodyAsString().contains("\"NO_CRITERIA\"")) {
                throw new NoCriteriaException();
            }
            if (e.getStatusCode().value() == 400) {
                throw new CalcRejectedException("계산 서버 입력 거부 " + path, e);
            }
            // 401(토큰 불일치) 등: 사용자가 고칠 수 없는 설정 문제 → 503
            throw new CalcUnavailableException("계산 서버 요청 오류 " + path + ": " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new CalcUnavailableException("계산 서버 연결 실패 " + path, e);
        }
        if (result == null) {
            throw new CalcUnavailableException("계산 서버 빈 응답 " + path, null);
        }
        return result;
    }
}
