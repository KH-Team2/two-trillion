package com.hoperefund.expense.client;

import com.hoperefund.common.exception.CalcUnavailableException;
import com.hoperefund.expense.dto.CardParseResultDTO;
import com.hoperefund.expense.exception.CardUploadException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 파이썬 계산 서버의 카드 파일 해석 API를 부른다: POST {CALC_BASE_URL}/card/parse
 *
 * <p>보내는 것 (multipart = 파일과 글자 값을 한 요청에 같이 담는 방식)
 *
 * <ul>
 *   <li>file: 사용자가 올린 파일 그대로 (csv · xlsx · xls)
 *   <li>payMethod: 사용자가 고른 결제수단 (CREDIT · CHECK · 사용구분 칸이 없는 파일일 때만)
 * </ul>
 *
 * <p>받는 것: 성공 200 {"rowCount", "rows": [...]} · 실패 400 {"error": "코드", "message": "문구",
 * "errorRows": [...]}
 *
 * <p>헤더 X-Calc-Token: 계산 서버 calc/.env의 CALC_API_TOKEN과 같은 값 (backend/.env)
 *
 * <p>설정(calc.base-url 등)은 팀원의 계산 호출(prediction)과 같은 application.yml 값을 같이 쓴다.
 */
@Slf4j
@Component // 스프링이 이 클래스를 하나 만들어 두고, 필요한 곳(Service)에 넣어 줌
public class CardParseClient {

    // 계산 서버가 보내는 업로드 오류 코드 중 화면에 그대로 넘길 것 (API 명세서의 오류 코드와 같음)
    private static final Set<String> FILE_ERROR_CODES =
            Set.of("UNSUPPORTED_FORMAT", "PAY_METHOD_REQUIRED", "ROW_LIMIT", "VALIDATION");

    private final RestClient restClient;

    // @Value("${설정 이름}"): application.yml에서 값을 읽어 와서 넣어 줌 (":" 뒤는 값이 없을 때 기본값)
    public CardParseClient(
            @Value("${calc.base-url}") String baseUrl,
            @Value("${calc.api-token:}") String token,
            @Value("${calc.connect-timeout:3s}") Duration connectTimeout,
            @Value("${calc.read-timeout:10s}") Duration readTimeout) {
        // 연결 · 응답 제한 시간: 계산 서버가 꺼져 있거나 멈춰도 무한정 기다리지 않게
        // HTTP/1.1 고정: 계산 서버(uvicorn)가 HTTP/2 전환 요청을 거절해서 (팀원 HttpCalcClient와 같은 설정)
        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(connectTimeout)
                        .version(HttpClient.Version.HTTP_1_1)
                        .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        this.restClient =
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .requestFactory(factory)
                        .defaultHeader("X-Calc-Token", token)
                        .build();
    }

    /**
     * 파일을 계산 서버에 보내서 거래 목록 + 분류 결과를 받는다
     *
     * @param content 파일 내용 (바이트)
     * @param fileName 원본 파일명 (계산 서버가 확장자로 csv · xlsx · xls를 구분함)
     * @param payMethod 사용자가 고른 결제수단 (없으면 null)
     */
    public CardParseResultDTO parse(byte[] content, String fileName, String payMethod) {
        // multipart 본문 만들기: 이름("file", "payMethod") → 값
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        // ByteArrayResource: 바이트를 "파일"처럼 보내는 포장지 · getFilename()을 바꿔서 원본 파일명이 같이 가게 함
        form.add(
                "file",
                new ByteArrayResource(content) {
                    @Override
                    public String getFilename() {
                        return fileName;
                    }
                });
        if (payMethod != null) {
            form.add("payMethod", payMethod);
        }

        CardParseResultDTO result;
        try {
            result =
                    restClient
                            .post()
                            .uri("/card/parse")
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .body(form)
                            .retrieve() // 응답 받기 · 4xx/5xx면 예외가 남
                            .body(CardParseResultDTO.class); // 응답 JSON → DTO
        } catch (HttpClientErrorException e) {
            // 4xx: 파일 문제(400)면 계산 서버가 준 코드 · 문구를 그대로 화면에 넘김
            throw toUploadError(e);
        } catch (RestClientException e) {
            // 연결 실패 · 시간 초과 · 5xx → 공통 503 CALC_UNAVAILABLE
            throw new CalcUnavailableException("계산 서버 연결 실패 /card/parse", e);
        }
        if (result == null || result.getRows() == null) {
            throw new CalcUnavailableException("계산 서버 빈 응답 /card/parse", null);
        }
        return result;
    }

    /** 계산 서버 4xx 응답 → 업로드 오류로 바꾸기 */
    private RuntimeException toUploadError(HttpClientErrorException e) {
        if (e.getStatusCode().value() == 400) {
            CalcError body = readBody(e);
            if (body != null && FILE_ERROR_CODES.contains(body.error())) {
                return CardUploadException.badRequest(body.error(), body.message());
            }
            return CardUploadException.badRequest("VALIDATION", "파일을 확인해 주세요.");
        }
        // 401(토큰이 서로 다름) 등: 사용자가 고칠 수 없는 설정 문제 → 503 (원인은 로그로)
        return new CalcUnavailableException("계산 서버 요청 오류 /card/parse: " + e.getStatusCode(), e);
    }

    /** 오류 응답 본문 JSON 읽기 (모양이 다르면 null) */
    private CalcError readBody(HttpClientErrorException e) {
        try {
            return e.getResponseBodyAs(CalcError.class);
        } catch (RuntimeException ignored) {
            log.warn("계산 서버 오류 응답을 읽을 수 없음 /card/parse");
            return null;
        }
    }

    /**
     * 계산 서버의 오류 응답 모양 (record: 값만 담는 짧은 클래스)
     *
     * <p>예: {"error": "VALIDATION", "message": "3, 4번째 줄의 ... 확인해 주세요.", "errorRows": [3, 4]}
     */
    public record CalcError(String error, String message, List<Integer> errorRows) {}
}
