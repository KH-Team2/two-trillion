package com.hoperefund.expense.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hoperefund.common.exception.CalcUnavailableException;
import com.hoperefund.expense.dto.CardUploadResponseDTO;
import com.hoperefund.expense.dto.CategoryCountDTO;
import com.hoperefund.expense.exception.CardUploadException;
import com.hoperefund.expense.service.CardUploadService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 카드 업로드 API 테스트 (요청 · 응답 · 오류 코드)
 *
 * <p>@WebMvcTest: Controller와 오류 처리기(공통 + 업로드 전용)만 띄워서 가짜 HTTP 요청으로 확인
 *
 * <p>multipart("/주소"): 파일 업로드 요청 만들기 · .file(파일) · .param("이름", "값")
 */
@WebMvcTest(CardUploadController.class)
@AutoConfigureMockMvc(addFilters = false)
class CardUploadControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CardUploadService cardUploadService;

    private static final MockMultipartFile FILE =
            new MockMultipartFile("file", "a.csv", "text/csv", "내용".getBytes());

    @Test
    void 업로드_정상이면_200과_분류별_건수() throws Exception {
        CardUploadResponseDTO response =
                CardUploadResponseDTO.builder()
                        .uploadId(10L)
                        .fileName("a.csv")
                        .rowCount(3)
                        .categoryCounts(List.of(new CategoryCountDTO("TRANSIT", "대중교통", 3)))
                        .build();
        when(cardUploadService.upload(eq(1L), any(), eq("CHECK"), eq(false))).thenReturn(response);

        mockMvc.perform(
                        multipart("/api/card-uploads")
                                .file(FILE)
                                .param("payMethod", "CHECK")
                                .header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.uploadId").value(10))
                .andExpect(jsonPath("$.data.rowCount").value(3))
                .andExpect(jsonPath("$.data.categoryCounts[0].categoryCode").value("TRANSIT"))
                .andExpect(jsonPath("$.data.categoryCounts[0].count").value(3));
    }

    @Test
    void 계속을_누른_요청은_allowDuplicate_true로_넘긴다() throws Exception {
        when(cardUploadService.upload(eq(1L), any(), isNull(), eq(true)))
                .thenReturn(CardUploadResponseDTO.builder().uploadId(10L).build());

        mockMvc.perform(
                        multipart("/api/card-uploads")
                                .file(FILE)
                                .param("allowDuplicate", "true")
                                .header("X-Member-Id", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void 중복_경고면_409_DUPLICATE_WARNING() throws Exception {
        when(cardUploadService.upload(anyLong(), any(), any(), anyBoolean()))
                .thenThrow(
                        new CardUploadException(
                                409, "DUPLICATE_WARNING", "이미 올린 내역과 같은 결제가 2건 있어요"));

        mockMvc.perform(multipart("/api/card-uploads").file(FILE).header("X-Member-Id", "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_WARNING"))
                .andExpect(jsonPath("$.error.message").value("이미 올린 내역과 같은 결제가 2건 있어요"));
    }

    @Test
    void 사용구분_선택이_필요하면_400_PAY_METHOD_REQUIRED() throws Exception {
        when(cardUploadService.upload(anyLong(), any(), any(), anyBoolean()))
                .thenThrow(
                        CardUploadException.badRequest(
                                "PAY_METHOD_REQUIRED", "신용카드인지 체크카드인지 선택해 주세요."));

        mockMvc.perform(multipart("/api/card-uploads").file(FILE).header("X-Member-Id", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PAY_METHOD_REQUIRED"));
    }

    @Test
    void 파일이_없으면_400_VALIDATION() throws Exception {
        mockMvc.perform(multipart("/api/card-uploads").header("X-Member-Id", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"));
    }

    @Test
    void 회원번호_헤더가_없으면_401() throws Exception {
        mockMvc.perform(multipart("/api/card-uploads").file(FILE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 계산_서버가_꺼져_있으면_503_CALC_UNAVAILABLE() throws Exception {
        when(cardUploadService.upload(anyLong(), any(), any(), anyBoolean()))
                .thenThrow(new CalcUnavailableException("연결 실패", null));

        mockMvc.perform(multipart("/api/card-uploads").file(FILE).header("X-Member-Id", "1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("CALC_UNAVAILABLE"));
    }
}
