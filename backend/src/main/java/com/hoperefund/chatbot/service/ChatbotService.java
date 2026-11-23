package com.hoperefund.chatbot.service;

import com.hoperefund.chatbot.client.FaqClient;
import com.hoperefund.common.exception.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

// FAQ 조회, 질문 처리 및 회원별 요청 횟수 제한
@Service
public class ChatbotService {

    // 최근 30초 동안 최대 10회 요청 허용
    private static final int MAX_REQUESTS = 10;
    private static final Duration WINDOW = Duration.ofSeconds(30);

    private final FaqClient faqClient;

    // 회원번호별 요청 시각 저장
    private final ConcurrentHashMap<Long, Deque<Instant>> requests = new ConcurrentHashMap<>();

    public ChatbotService(FaqClient faqClient) {
        this.faqClient = faqClient;
    }

    // FAQ 카테고리 목록
    public JsonNode categories() {
        return faqClient.get("/faq/categories", null);
    }

    // FAQ 전체 목록 및 카테고리별 조회
    public JsonNode list(String category) {
        return faqClient.get("/faq", category);
    }

    // 버튼형 FAQ 상세 조회
    public JsonNode detail(Long memberId, String faqId) {
        checkRateLimit(memberId);
        return faqClient.get("/faq/" + faqId, null);
    }

    // 자유 입력 질문
    public JsonNode ask(Long memberId, String question) {
        checkRateLimit(memberId);
        return faqClient.ask(question);
    }

    // 회원별 요청 횟수 검사
    private void checkRateLimit(Long memberId) {
        Instant now = Instant.now();

        // 회원별 요청 기록 조회 또는 생성
        Deque<Instant> history = requests.computeIfAbsent(memberId, id -> new ArrayDeque<>());

        synchronized (history) {

            // 최근 30초를 벗어난 요청 기록 제거
            Instant cutoff = now.minus(WINDOW);

            while (!history.isEmpty() && !history.peekFirst().isAfter(cutoff)) {
                history.removeFirst();
            }

            // 10회 이상 요청했다면 HTTP 429 반환
            if (history.size() >= MAX_REQUESTS) {
                throw new BusinessException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "RATE_LIMIT_EXCEEDED",
                        "30초 동안 최대 10회까지 질문할 수 있습니다. 잠시 후 다시 시도해 주세요.");
            }

            // 현재 요청 시각 기록
            history.addLast(now);
        }
    }
}
