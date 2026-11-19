package com.hoperefund.chatbot.controller;

import com.hoperefund.chatbot.service.ChatbotService;
import com.hoperefund.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// React의 API 요청 접수
@RestController
@RequestMapping("/api/chatbot/faq")
public class ChatbotController {
    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    public record FaqAskRequest(
    		@NotBlank
    		@Size(max = 100)
    		String question
	) {}

    @PostMapping("/ask")
    public ApiResponse<JsonNode> ask(@Valid @RequestBody FaqAskRequest request) {
    	return ApiResponse.ok(chatbotService.ask(request.question()));
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
