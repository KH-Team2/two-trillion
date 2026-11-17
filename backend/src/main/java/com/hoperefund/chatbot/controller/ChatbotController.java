package com.hoperefund.chatbot.controller;

import tools.jackson.databind.JsonNode;
import com.hoperefund.chatbot.service.ChatbotService;
import com.hoperefund.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chatbot/faq")
public class ChatbotController {
    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @GetMapping("/categories")
    public ApiResponse<JsonNode> categories() {
        return ApiResponse.ok(chatbotService.categories());
    }

    @GetMapping
    public ApiResponse<JsonNode> list(@RequestParam(required = false) String category) {
        return ApiResponse.ok(chatbotService.list(category));
    }

    @GetMapping("/{faqId:TAX_[0-9]{3}}")
    public ApiResponse<JsonNode> detail(@PathVariable String faqId) {
        return ApiResponse.ok(chatbotService.detail(faqId));
    }
}
