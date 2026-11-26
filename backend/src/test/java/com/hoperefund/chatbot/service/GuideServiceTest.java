package com.hoperefund.chatbot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hoperefund.chatbot.client.GuideClient;
import com.hoperefund.chatbot.dto.GuideInputDTO;
import com.hoperefund.chatbot.mapper.GuideMapper;
import com.hoperefund.common.exception.BusinessException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class GuideServiceTest {

    // 실제 DB 대신 준비된 입력값을 반환하는 가짜 Mapper
    private GuideService serviceWith(GuideInputDTO input) {
        GuideMapper mapper =
                (GuideMapper)
                        Proxy.newProxyInstance(
                                GuideMapper.class.getClassLoader(),
                                new Class<?>[] {GuideMapper.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("findGuideInput")) {
                                        return input;
                                    }
                                    throw new UnsupportedOperationException(method.getName());
                                });

        // 추천 조건 테스트에서는 FastAPI를 호출하지 않으므로 Mock 사용
        return createService(mapper, mock(GuideClient.class));
    }

    // 테스트에 사용할 임시 입력값 생성
    private GuideInputDTO input(
            long salary, String homeless, long housing, long pension, long irp) {

        GuideInputDTO dto = new GuideInputDTO();
        dto.setTotalSalary(salary);
        dto.setHomelessHeadYn(homeless);
        dto.setHousingSaving(housing);
        dto.setPensionSaving(pension);
        dto.setIrpSaving(irp);
        return dto;
    }

    // 1. 세 가지 추천 조건을 모두 충족
    @Test
    void recommendsAllThree() {
        var service = serviceWith(input(45_000_000L, "Y", 0L, 0L, 0L));

        assertEquals(
                List.of("HOUSING_SAVINGS", "PENSION_SAVINGS", "IRP"), service.recommend(1L, 101L));
    }

    // 2. 총급여가 정확히 7,000만 원이면 주택청약 추천
    @Test
    void recommendsHousingAtSalaryLimit() {
        var service = serviceWith(input(70_000_000L, "Y", 0L, 500_000L, 500_000L));

        assertEquals(List.of("HOUSING_SAVINGS"), service.recommend(1L, 101L));
    }

    // 3. 총급여가 7,000만 원을 초과하면 주택청약 제외
    @Test
    void excludesHousingAboveSalaryLimit() {
        var service = serviceWith(input(70_000_001L, "Y", 0L, 0L, 0L));

        assertEquals(List.of("PENSION_SAVINGS", "IRP"), service.recommend(1L, 101L));
    }

    // 4. 주택청약 납입액이 0원이 아니면 주택청약 제외
    @Test
    void excludesHousingWhenAlreadyPaid() {
        var service = serviceWith(input(45_000_000L, "Y", 100_000L, 0L, 0L));

        assertEquals(List.of("PENSION_SAVINGS", "IRP"), service.recommend(1L, 101L));
    }

    // 5. 모든 추천 조건을 충족하지 않으면 빈 목록 반환
    @Test
    void recommendsNothing() {
        var service = serviceWith(input(80_000_000L, "N", 100_000L, 100_000L, 100_000L));

        assertTrue(service.recommend(1L, 101L).isEmpty());
    }

    // 6. 계산 결과가 없으면 예외 발생
    @Test
    void rejectsMissingPrediction() {
        var service = serviceWith(null);

        assertThrows(BusinessException.class, () -> service.recommend(1L, 101L));
    }

    // 7. 이미 저장된 추천은 Gemma를 다시 호출하지 않음
    @Test
    void reusesSavedRecommendation() throws Exception {
        GuideMapper mapper = mock(GuideMapper.class);
        GuideClient client = mock(GuideClient.class);
        GuideService service = createService(mapper, client);

        // 연금저축만 추천되는 입력값
        when(mapper.findGuideInput(1L, 101L)).thenReturn(input(80_000_000L, "N", 100L, 0L, 100L));

        // DB에 이미 연금저축 설명이 저장된 상황
        String savedJson =
                """
                {
                  "code": "PENSION_SAVINGS",
                  "name": "연금저축",
                  "content": {
                    "description": "연금저축은 노후 준비 상품입니다.",
                    "taxBenefit": "세액공제 대상이 될 수 있습니다.",
                    "nextStep": "수수료를 확인해 보세요."
                  },
                  "source": "gemma"
                }
                """;

        when(mapper.findRecommendationContents(1L, 101L)).thenReturn(List.of(savedJson));

        // 추천 결과 조회
        Map<String, Object> result = service.generate(1L, 101L);
        List<?> items = (List<?>) result.get("items");

        assertEquals(1, items.size());
        assertEquals("PENSION_SAVINGS", ((JsonNode) items.get(0)).get("code").asText());

        // 이미 저장되어 있으므로 새로 생성하거나 저장하지 않음
        verify(client, never()).generate(anyList());
        verify(mapper, never())
                .insertRecommendation(anyLong(), anyLong(), anyString(), anyString());
    }

    // 8. 저장되지 않은 추천만 Gemma로 생성하고 DB 저장
    @Test
    void generatesOnlyMissingRecommendations() throws Exception {
        GuideMapper mapper = mock(GuideMapper.class);
        GuideClient client = mock(GuideClient.class);
        GuideService service = createService(mapper, client);
        when(mapper.lockPrediction(1L, 101L)).thenReturn(101L);
        // 주택청약과 연금저축 추천 대상
        when(mapper.findGuideInput(1L, 101L)).thenReturn(input(45_000_000L, "Y", 0L, 0L, 100L));

        ObjectMapper json = new ObjectMapper();

        // 연금저축은 이미 DB에 저장됨
        String savedPension =
                """
                {
                  "code": "PENSION_SAVINGS",
                  "name": "연금저축",
                  "content": {
                    "description": "연금저축 상품 설명",
                    "taxBenefit": "연금저축 세액공제 설명",
                    "nextStep": "연금저축 확인 사항"
                  },
                  "source": "gemma"
                }
                """;

        when(mapper.findRecommendationContents(1L, 101L)).thenReturn(List.of(savedPension));

        // FastAPI는 누락된 주택청약 설명만 반환
        JsonNode apiResponse =
                json.readTree(
                        """
                {
                  "items": [
                    {
                      "code": "HOUSING_SAVINGS",
                      "name": "주택청약종합저축",
                      "content": {
                        "description": "주택청약 상품 설명",
                        "taxBenefit": "주택청약 소득공제 설명",
                        "nextStep": "주택청약 확인 사항"
                      },
                      "source": "gemma"
                    }
                  ]
                }
                """);

        when(client.generate(List.of("HOUSING_SAVINGS"))).thenReturn(apiResponse);

        // DB INSERT 성공했다고 가정
        when(mapper.insertRecommendation(eq(1L), eq(101L), anyString(), eq("gemma3:4b")))
                .thenReturn(1);

        Map<String, Object> result = service.generate(1L, 101L);
        List<?> items = (List<?>) result.get("items");

        // 기존 연금저축과 신규 주택청약을 함께 반환
        assertEquals(2, items.size());
        assertEquals("HOUSING_SAVINGS", ((JsonNode) items.get(0)).get("code").asText());
        assertEquals("PENSION_SAVINGS", ((JsonNode) items.get(1)).get("code").asText());

        // 주택청약만 새로 생성하고 저장했는지 검증
        verify(client).generate(List.of("HOUSING_SAVINGS"));
        verify(mapper).insertRecommendation(eq(1L), eq(101L), anyString(), eq("gemma3:4b"));
        verify(mapper).lockPrediction(1L, 101L);
    }

    // 9. 추천 대상이 없으면 DB 조회 및 AI 생성 생략
    @Test
    void skipsGenerationWhenNoRecommendations() {
        GuideMapper mapper = mock(GuideMapper.class);
        GuideClient client = mock(GuideClient.class);
        GuideService service = createService(mapper, client);

        // 세 조건 모두 불충족
        when(mapper.findGuideInput(1L, 101L)).thenReturn(input(80_000_000L, "N", 100L, 100L, 100L));

        Map<String, Object> result = service.generate(1L, 101L);
        List<?> items = (List<?>) result.get("items");

        assertTrue(items.isEmpty());

        // 저장된 추천도 조회하지 않고, FastAPI도 호출하지 않음
        verify(mapper, never()).findRecommendationContents(anyLong(), anyLong());
        verifyNoInteractions(client);
    }

    // 10. GET 조회는 저장된 추천만 반환하고 FastAPI는 호출하지 않음
    @Test
    void getsOnlySavedRecommendations() {
        GuideMapper mapper = mock(GuideMapper.class);
        GuideClient client = mock(GuideClient.class);
        GuideService service = createService(mapper, client);

        // 주택청약과 IRP가 추천 대상
        when(mapper.findGuideInput(1L, 101L)).thenReturn(input(45_000_000L, "Y", 0L, 100L, 0L));

        // DB에는 IRP 설명만 저장되어 있음
        String savedIrp =
                """
                {
                  "code": "IRP",
                  "name": "개인형 퇴직연금(IRP)",
                  "content": {
                    "description": "퇴직급여와 추가 납입금을 관리하는 연금계좌입니다.",
                    "taxBenefit": "추가 납입액은 세액공제 대상이 될 수 있습니다.",
                    "nextStep": "중도 인출 제한을 확인해 보세요."
                  },
                  "source": "gemma"
                }
                """;

        when(mapper.findRecommendationContents(1L, 101L)).thenReturn(List.of(savedIrp));

        Map<String, Object> result = service.getSaved(1L, 101L);
        List<?> items = (List<?>) result.get("items");

        // 저장된 IRP만 반환
        assertEquals(1, items.size());
        assertEquals("IRP", ((JsonNode) items.get(0)).get("code").asText());

        // Gemma 호출 및 DB 저장이 발생하지 않았는지 확인
        verifyNoInteractions(client);
        verify(mapper, never())
                .insertRecommendation(anyLong(), anyLong(), anyString(), anyString());
    }

    // 실제 DB 없이 트랜잭션을 사용하는 서비스 테스트
    private GuideService createService(GuideMapper mapper, GuideClient client) {

        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(mock(TransactionStatus.class));

        return new GuideServiceImpl(mapper, client, transactionManager);
    }
}
