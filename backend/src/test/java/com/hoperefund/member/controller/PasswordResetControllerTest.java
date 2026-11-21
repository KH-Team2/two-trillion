package com.hoperefund.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.common.exception.GlobalExceptionHandler;
import com.hoperefund.member.dto.LoginIdLookupResponseDTO;
import com.hoperefund.member.dto.PasswordResetResponseDTO;
import com.hoperefund.member.service.PasswordResetService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 아이디 찾기 · 비밀번호 재설정 API 테스트 (F-01-03)
 * 요청 형식 검증 · 응답 봉투 · Service 오류가 그대로 나가는지 확인한다.
 */
@WebMvcTest(PasswordResetController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PasswordResetControllerTest {

    private static final String COMPLETE_BODY =
            "{\"email\": \"hope@example.com\", \"code\": \"123456\","
                    + " \"newPassword\": \"newPassword1!\", \"newPasswordConfirm\": \"newPassword1!\"}";

    @Autowired private MockMvc mockMvc;

    // JSON 본문으로 POST 요청
    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @MockitoBean private PasswordResetService passwordResetService;

    // 아이디 찾기 → 200 {data: {maskedLoginId}}
    @Test
    void findLoginIdReturns200() throws Exception {

        given(passwordResetService.findLoginId("hope@example.com"))
                .willReturn(new LoginIdLookupResponseDTO("te******"));

        postJson("/api/auth/login-id-lookups", "{\"email\": \"hope@example.com\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.maskedLoginId").value("te******"));
    }

    // 가입되지 않은 이메일 → 404 「가입 정보를 찾을 수 없어요」
    @Test
    void findLoginIdUnknownEmailReturns404() throws Exception {

        String message = "가입 정보를 찾을 수 없어요";
        BusinessException error = new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
        given(passwordResetService.findLoginId("nobody@example.com")).willThrow(error);

        postJson("/api/auth/login-id-lookups", "{\"email\": \"nobody@example.com\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("가입 정보를 찾을 수 없어요"));
    }

    // 잘못된 이메일 형식 → 400 · Service 를 부르지 않음
    @Test
    void invalidEmailReturns400() throws Exception {

        postJson("/api/auth/password-resets", "{\"email\": \"not-an-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("이메일 형식을 확인해 주세요"));

        verify(passwordResetService, never()).sendCode(any());
    }

    // 코드 발송 → 200 {data: {expiresAt, devCode}}
    @Test
    void sendCodeReturns200() throws Exception {

        Instant expiresAt = Instant.parse("2026-11-21T03:10:00Z");
        PasswordResetResponseDTO response = new PasswordResetResponseDTO(expiresAt, null);
        given(passwordResetService.sendCode("hope@example.com")).willReturn(response);

        postJson("/api/auth/password-resets", "{\"email\": \"hope@example.com\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresAt").exists());
    }

    // 재설정 완료 → 200 · data 는 null
    @Test
    void completeReturns200() throws Exception {

        postJson("/api/auth/password-resets/completion", COMPLETE_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(passwordResetService).complete(any());
    }

    // 코드가 6자리 숫자가 아님 → 400
    @Test
    void completeInvalidCodeReturns400() throws Exception {

        postJson("/api/auth/password-resets/completion", COMPLETE_BODY.replace("123456", "12ab"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("6자리 인증 코드를 입력해 주세요"));

        verify(passwordResetService, never()).complete(any());
    }

    // 새 비밀번호가 8자 미만 → 400
    @Test
    void completeShortPasswordReturns400() throws Exception {

        String body = COMPLETE_BODY.replace("newPassword1!", "short");

        postJson("/api/auth/password-resets/completion", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("비밀번호는 8자 이상 입력해 주세요"));
    }

    // 만료 · 5번 틀림 → 400 「인증 코드를 다시 받아 주세요」
    @Test
    void completeExpiredCodeReturns400() throws Exception {

        String message = "인증 코드를 다시 받아 주세요";
        BusinessException error =
                new BusinessException(HttpStatus.BAD_REQUEST, "RESET_CODE_EXPIRED", message);
        willThrow(error).given(passwordResetService).complete(any());

        postJson("/api/auth/password-resets/completion", COMPLETE_BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("RESET_CODE_EXPIRED"))
                .andExpect(jsonPath("$.error.message").value("인증 코드를 다시 받아 주세요"));
    }
}
