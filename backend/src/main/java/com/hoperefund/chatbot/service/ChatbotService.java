package com.hoperefund.chatbot.service;

import com.hoperefund.chatbot.client.FaqClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

// 요청에 맞는 FAQ 조회 작업 연결
@Service
public class ChatbotService {
    private final FaqClient faqClient;

    public ChatbotService(FaqClient faqClient) {
        this.faqClient = faqClient;
    }

    public JsonNode categories() {
        return faqClient.get("/faq/categories", null);
    }

    public JsonNode list(String category) {
        return faqClient.get("/faq", category);
    }

    public JsonNode detail(String faqId) {
        return faqClient.get("/faq/" + faqId, null);
    }

    public JsonNode ask(String question) {
        return faqClient.ask(question);
    }
}
