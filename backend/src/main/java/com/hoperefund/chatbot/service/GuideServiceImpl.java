package com.hoperefund.chatbot.service;

import com.hoperefund.chatbot.client.GuideClient;
import com.hoperefund.chatbot.dto.GuideInputDTO;
import com.hoperefund.chatbot.mapper.GuideMapper;
import com.hoperefund.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class GuideServiceImpl implements GuideService {

    private final GuideMapper guideMapper;
    private final GuideClient guideClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GuideServiceImpl(GuideMapper guideMapper, GuideClient guideClient) {
        this.guideMapper = guideMapper;
        this.guideClient = guideClient;
    }

    // 본인 계산 결과의 입력값을 기준으로 추천 코드 선정
    @Override
    public List<String> recommend(long memberId, long predictionId) {

        // 1. 계산 결과에 저장된 입력값 조회
        GuideInputDTO input = guideMapper.findGuideInput(memberId, predictionId);

        // 계산 결과가 없거나 다른 회원의 결과라면 404
        if (input == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "계산 결과를 찾을 수 없습니다.");
        }

        List<String> codes = new ArrayList<>();

        // 2. 주택청약종합저축
        if (input.getTotalSalary() != null
                && input.getTotalSalary() <= 70_000_000L
                && "Y".equals(input.getHomelessHeadYn())
                && Long.valueOf(0L).equals(input.getHousingSaving())) {

            codes.add("HOUSING_SAVINGS");
        }

        // 3. 연금저축
        if (Long.valueOf(0L).equals(input.getPensionSaving())) {
            codes.add("PENSION_SAVINGS");
        }

        // 4. IRP
        if (Long.valueOf(0L).equals(input.getIrpSaving())) {
            codes.add("IRP");
        }

        // 5. 추천 코드 목록 반환
        return codes;
    }

    // 추천 결과가 있으면 DB에서 재사용하고, 없으면 생성 후 저장
    @Override
    public Map<String, Object> generate(long memberId, long predictionId) {

        // 1. 추천 조건 판단
        List<String> codes = recommend(memberId, predictionId);

        // 추천 항목이 없으면 AI 호출 없이 종료
        if (codes.isEmpty()) {
            return Map.of("predictionId", predictionId, "items", List.of());
        }

        // 2. 기존 DB 추천 결과 조회
        List<String> savedContents = guideMapper.findRecommendationContents(memberId, predictionId);

        Map<String, JsonNode> saved = new HashMap<>();

        for (String content : savedContents) {
            try {
                JsonNode item = objectMapper.readTree(content);

                if (item == null
                        || !item.isObject()
                        || !item.path("code").isTextual()
                        || !item.path("content").isObject()) {
                    throw new IllegalStateException("저장된 추천 JSON 형식이 올바르지 않습니다.");
                }

                String code = item.get("code").asText();

                if (saved.putIfAbsent(code, item) != null) {
                    throw new IllegalStateException("중복 저장된 추천 항목이 있습니다.");
                }

            } catch (JacksonException e) {
                throw new IllegalStateException("저장된 추천 JSON을 읽을 수 없습니다.", e);
            }
        }

        // 3. 저장되지 않은 추천 코드만 추출
        List<String> missingCodes =
                codes.stream().filter(code -> !saved.containsKey(code)).toList();

        // 4. 누락된 추천만 FastAPI에서 생성
        if (!missingCodes.isEmpty()) {

            JsonNode response = guideClient.generate(missingCodes);

            for (JsonNode item : response.get("items")) {

                String code = item.path("code").asText();

                if (!missingCodes.contains(code)
                        || saved.containsKey(code)
                        || !item.path("content").isObject()) {
                    throw new IllegalStateException("절세 가이드 응답이 올바르지 않습니다.");
                }

                String source = item.path("source").asText();

                if (!"gemma".equals(source) && !"fallback".equals(source)) {
                    throw new IllegalStateException("절세 가이드 생성 출처가 올바르지 않습니다.");
                }

                // JSON 객체 전체를 CLOB에 문자열로 저장
                String content = item.toString();

                // 기본 설명이면 실제 LLM 생성으로 기록하지 않음
                String llmModel = "gemma".equals(source) ? "gemma3:4b" : null;

                int inserted =
                        guideMapper.insertRecommendation(memberId, predictionId, content, llmModel);

                if (inserted != 1) {
                    throw new IllegalStateException("절세 가이드 저장에 실패했습니다.");
                }

                saved.put(code, item);
            }

            // 요청한 코드가 모두 생성되었는지 확인
            if (missingCodes.stream().anyMatch(code -> !saved.containsKey(code))) {
                throw new IllegalStateException("일부 절세 가이드가 생성되지 않았습니다.");
            }
        }

        // 5. 기존 추천 순서대로 결과 반환
        List<JsonNode> items = new ArrayList<>();

        for (String code : codes) {
            items.add(saved.get(code));
        }

        return Map.of(
                "predictionId", predictionId,
                "items", items);
    }

    // 해당 계산 결과에 이미 저장된 추천 설명만 조회
    @Override
    public Map<String, Object> getSaved(long memberId, long predictionId) {

        // 1. 본인 계산 결과인지 확인하고 추천 코드 선정
        List<String> codes = recommend(memberId, predictionId);

        // 추천 대상이 없으면 빈 목록 반환
        if (codes.isEmpty()) {
            return Map.of("predictionId", predictionId, "items", List.of());
        }

        // 2. Oracle에 저장된 추천 JSON 문자열 조회
        List<String> contents = guideMapper.findRecommendationContents(memberId, predictionId);

        Map<String, JsonNode> saved = new HashMap<>();

        // 3. JSON 문자열을 객체로 변환
        for (String content : contents) {
            try {
                JsonNode item = objectMapper.readTree(content);

                if (item == null
                        || !item.isObject()
                        || !item.path("code").isTextual()
                        || !item.path("content").isObject()) {
                    throw new IllegalStateException("저장된 추천 JSON 형식이 올바르지 않습니다.");
                }

                String code = item.get("code").asText();

                // 현재 계산 결과의 추천 대상에 해당하는 항목만 사용
                if (codes.contains(code)) {
                    if (saved.putIfAbsent(code, item) != null) {
                        throw new IllegalStateException("중복 저장된 추천 항목이 있습니다.");
                    }
                }

            } catch (JacksonException e) {
                throw new IllegalStateException("저장된 추천 JSON을 읽을 수 없습니다.", e);
            }
        }

        // 4. 추천 순서대로 저장된 항목만 반환
        List<JsonNode> items = new ArrayList<>();

        for (String code : codes) {
            if (saved.containsKey(code)) {
                items.add(saved.get(code));
            }
        }

        return Map.of(
                "predictionId", predictionId,
                "items", items);
    }
}
