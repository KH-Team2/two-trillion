package com.hoperefund.chatbot.controller;

import com.hoperefund.chatbot.service.ChatbotService;
import com.hoperefund.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

// React의 FAQ 조회 및 질문 분류 요청 접수
@RestController
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    public record FaqAskRequest(@NotBlank @Size(max = 100) String question) {}

    // FAQ 카테고리 목록
    @GetMapping("/api/faq-categories")
    public ApiResponse<JsonNode> categories() {
        return ApiResponse.ok(chatbotService.categories());
    }

    // FAQ 전체 목록 및 카테고리별 조회
    @GetMapping("/api/faqs")
    public ApiResponse<JsonNode> list(@RequestParam(required = false) String category) {
        return ApiResponse.ok(chatbotService.list(category));
    }

    // FAQ 상세 조회
    @GetMapping("/api/faqs/{faqId:TAX_[0-9]{3}}")
    public ApiResponse<JsonNode> detail(@PathVariable String faqId) {
        return ApiResponse.ok(chatbotService.detail(faqId));
    }

    // 자유 질문을 FAQ 항목에 매칭
    @PostMapping("/api/faq-matches")
    public ApiResponse<JsonNode> ask(@Valid @RequestBody FaqAskRequest request) {
        return ApiResponse.ok(chatbotService.ask(request.question()));
    }
}
