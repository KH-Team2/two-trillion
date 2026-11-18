package com.hoperefund.chatbot.client;

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
    private final RestClient restClient;

    public FaqClient(
            @Value("${chatbot.base-url}") String baseUrl,
            @Value("${chatbot.api-token:}") String token,
            @Value("${chatbot.connect-timeout:3s}") Duration connectTimeout,
            @Value("${chatbot.read-timeout:60s}") Duration readTimeout) {
        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(connectTimeout)
                        .version(HttpClient.Version.HTTP_1_1)
                        .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl).requestFactory(factory);
        if (token != null && !token.isBlank()) {
            // FastAPI에서 동일한 헤더를 검증하도록 설정한 경우에만 사용합니다.
            builder.defaultHeader("X-Chatbot-Token", token);
        }
        restClient = builder.build();
    }

    public JsonNode get(String path, String category) {
        try {
            JsonNode result =
                    restClient
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
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "챗봇 서버에 연결할 수 없습니다.", e);
        }
    }

    public JsonNode ask(String question) {
    	try {
    		JsonNode result = restClient
    				.post()
    				.uri("/faq/ask")
    				.body(java.util.Map.of("question", question))
    				.retrieve()
    				.body(JsonNode.class);

    		if (result == null) {
    			throw new IllegalStateException("챗봇 서버 응답이 비어 있습니다.");
    		}

    		return result;
    	} catch (RestClientException | IllegalStateException e) {
    		throw new ResponseStatusException(
    				HttpStatus.SERVICE_UNAVAILABLE,
    				"AI 질문 분류 서비스를 이용할 수 없습니다.",
    				e
    				);
    	}
    }
}
