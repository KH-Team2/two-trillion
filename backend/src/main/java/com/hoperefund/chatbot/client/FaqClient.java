package com.hoperefund.chatbot.client;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.common.exception.ChatbotUnavailableException;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

// FastAPI 서버에 실제 HTTP 요청
@Component
public class FaqClient {
    private final RestClient faqRestClient;
    private final RestClient askRestClient;

    public FaqClient(
            @Value("${chatbot.base-url}") String baseUrl,
            @Value("${chatbot.api-token}") String token,
            @Value("${chatbot.connect-timeout:3s}") Duration connectTimeout,
            @Value("${chatbot.faq-read-timeout:5s}") Duration faqReadTimeout,
            @Value("${chatbot.read-timeout:60s}") Duration askReadTimeout) {

        if (token == null || token.isBlank()) {
            throw new IllegalStateException("CHATBOT_API_TOKEN이 설정되지 않았습니다.");
        }

        this.faqRestClient = createClient(baseUrl, token, connectTimeout, faqReadTimeout);

        this.askRestClient = createClient(baseUrl, token, connectTimeout, askReadTimeout);
    }

    private RestClient createClient(
            String baseUrl, String token, Duration connectTimeout, Duration readTimeout) {

        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(connectTimeout)
                        .version(HttpClient.Version.HTTP_1_1)
                        .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);

        factory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("X-Chatbot-Token", token)
                .build();
    }

    public JsonNode get(String path, String category) {
        try {
            JsonNode result =
                    faqRestClient
                            .get()
                            .uri(
                                    uriBuilder -> {
                                        uriBuilder.path(path);
                                        if (category != null && !category.isBlank()) {
                                            uriBuilder.queryParam("category", category);
                                        }
                                        return uriBuilder.build();
                                    })
                            .retrieve()
                            .body(JsonNode.class);
            if (result == null) throw new IllegalStateException("챗봇 서버 응답이 비어 있습니다.");
            return result;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 FAQ를 찾을 수 없습니다.");
        } catch (RestClientException | IllegalStateException e) {
            throw new ChatbotUnavailableException("챗봇 서버 연결 실패", e);
        }
    }

    public JsonNode ask(String question) {
        try {
            JsonNode result =
                    askRestClient
                            .post()
                            .uri("/faq/ask")
                            .body(java.util.Map.of("question", question))
                            .retrieve()
                            .body(JsonNode.class);

            if (result == null) {
                throw new IllegalStateException("챗봇 서버 응답이 비어 있습니다.");
            }

            return result;
        } catch (HttpClientErrorException.BadRequest e) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "PERSONAL_INFO_DETECTED",
                    "세금 용어 설명만 제공하므로 개인정보의 위험이 있는 질문은 전송할 수 없습니다.");
        } catch (RestClientException | IllegalStateException e) {
            throw new ChatbotUnavailableException("AI 질문 분류 서비스 오류", e);
        }
    }
}
