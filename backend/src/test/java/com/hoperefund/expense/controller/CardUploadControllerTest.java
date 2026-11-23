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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

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

    /**
     * 로그인한 회원으로 요청 (JWT 필터가 하는 일을 대신: principal = 회원번호)
     *
     * <p>[수정 11/19 고은] X-Member-Id 헤더 → 로그인 정보 · 이유: Controller가 @AuthenticationPrincipal로
     * 회원번호를 받게 바뀜 (소득 · 계산 API 테스트와 같은 방식) · 토큰 검사와 401은 SecurityConfigTest에서 확인
     *
     * <p>SecurityContextHolder: 「지금 요청한 사람이 누구인지」를 담아 두는 곳 · 여기에 회원번호를 직접 넣어 둠
     */
    private static RequestPostProcessor loginAs(long memberId) {
        return request -> {
            SecurityContextHolder.getContext()
                    .setAuthentication(
                            new UsernamePasswordAuthenticationToken(memberId, null, List.of()));
            return request;
        };
    }

    // 로그인 정보가 다음 테스트로 넘어가지 않게 지움 (@AfterEach: 테스트 하나가 끝날 때마다 실행)
    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

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
                                .with(loginAs(1L)))
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
                                .with(loginAs(1L)))
                .andExpect(status().isOk());
    }

    @Test
    void 중복_경고면_409_DUPLICATE_WARNING() throws Exception {
        when(cardUploadService.upload(anyLong(), any(), any(), anyBoolean()))
                .thenThrow(
                        new CardUploadException(
                                409, "DUPLICATE_WARNING", "이미 올린 내역과 같은 결제가 2건 있어요"));

        mockMvc.perform(multipart("/api/card-uploads").file(FILE).with(loginAs(1L)))
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

        mockMvc.perform(multipart("/api/card-uploads").file(FILE).with(loginAs(1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PAY_METHOD_REQUIRED"));
    }

    @Test
    void 파일이_없으면_400_VALIDATION() throws Exception {
        mockMvc.perform(multipart("/api/card-uploads").with(loginAs(1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"));
    }

    @Test
    void 계산_서버가_꺼져_있으면_503_CALC_UNAVAILABLE() throws Exception {
        when(cardUploadService.upload(anyLong(), any(), any(), anyBoolean()))
                .thenThrow(new CalcUnavailableException("연결 실패", null));

        mockMvc.perform(multipart("/api/card-uploads").file(FILE).with(loginAs(1L)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("CALC_UNAVAILABLE"));
    }
}
