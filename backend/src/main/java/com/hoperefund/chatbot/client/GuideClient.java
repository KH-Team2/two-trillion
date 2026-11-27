package com.hoperefund.chatbot.client;

import com.hoperefund.common.exception.ChatbotUnavailableException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Component
public class GuideClient {

    private final RestClient restClient;

    public GuideClient(
            @Value("${chatbot.base-url}") String baseUrl,
            @Value("${chatbot.api-token}") String token,
            @Value("${chatbot.connect-timeout:3s}") Duration connectTimeout,
            @Value("${chatbot.guide-read-timeout:200s}")
                    Duration readTimeout) { // 구현 후 실제 대기 시간을 보고 수정 필요

        if (token == null || token.isBlank()) {
            throw new IllegalStateException("CHATBOT_API_TOKEN이 설정되지 않았습니다.");
        }

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
                        .defaultHeader("X-Chatbot-Token", token)
                        .build();
    }

    // 선정된 추천 코드들을 FastAPI에 전달하여 설명 생성
    public JsonNode generate(List<String> codes) {

        try {
            JsonNode result =
                    restClient
                            .post()
                            .uri("/guide/generate")
                            .body(Map.of("codes", codes))
                            .retrieve()
                            .body(JsonNode.class);

            if (result == null || !result.has("items") || !result.get("items").isArray()) {
                throw new IllegalStateException("절세 가이드 서버 응답이 올바르지 않습니다.");
            }

            return result;

        } catch (RestClientException | IllegalStateException e) {
            throw new ChatbotUnavailableException("절세 가이드 생성 서버 연결 또는 응답 오류", e);
        }
    }
}
