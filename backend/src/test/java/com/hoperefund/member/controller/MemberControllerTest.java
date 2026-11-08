package com.hoperefund.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.common.exception.GlobalExceptionHandler;
import com.hoperefund.member.dto.MemberSignupResponseDTO;
import com.hoperefund.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 회원가입 API 테스트
 * 요청 형식 검증 · 성공 응답 · 업무 오류 응답 형식을 확인한다.
 */
@WebMvcTest(MemberController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MemberControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private MemberService memberService;

    private static final String VALID_BODY =
            """
            {
              "loginId": "hope_user",
              "email": "hope@example.com",
              "password": "password123",
              "passwordConfirm": "password123",
              "birthDate": "2000-01-15",
              "gender": "F",
              "termsAgreed": true,
              "privacyAgreed": true,
              "financeAgreed": true,
              "termsVersion": "v1.0",
              "privacyVersion": "v1.0",
              "financeVersion": "v1.0"
            }
            """;

    // 정상 가입 요청 → 201
    @Test
    void signupReturns201() throws Exception {

        given(memberService.signup(any()))
                .willReturn(new MemberSignupResponseDTO(1L, "hope_user", "회원가입이 완료되었습니다."));

        mockMvc.perform(
                        post("/api/members")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").value(1))
                .andExpect(jsonPath("$.loginId").value("hope_user"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    // 필수 동의 누락 → 400 VALIDATION, 서비스 호출 안 함
    @Test
    void missingAgreementReturns400() throws Exception {

        String body = VALID_BODY.replace("\"financeAgreed\": true", "\"financeAgreed\": false");

        mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION"));

        verify(memberService, never()).signup(any());
    }

    // 잘못된 이메일 · 짧은 비밀번호 → 400 VALIDATION
    @Test
    void invalidFieldsReturn400() throws Exception {

        String body =
                VALID_BODY
                        .replace("hope@example.com", "not-an-email")
                        .replace("\"password\": \"password123\"", "\"password\": \"short\"");

        mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION"));
    }

    // 성별 값 범위 밖 → 400 VALIDATION
    @Test
    void invalidGenderReturns400() throws Exception {

        String body = VALID_BODY.replace("\"gender\": \"F\"", "\"gender\": \"여성\"");

        mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION"));
    }

    // 중복 아이디 → 409, GlobalExceptionHandler 형식
    @Test
    void duplicateLoginIdReturns409() throws Exception {

        given(memberService.signup(any()))
                .willThrow(
                        new BusinessException(
                                HttpStatus.CONFLICT, "DUPLICATE_LOGIN_ID", "이미 사용 중인 아이디입니다."));

        mockMvc.perform(
                        post("/api/members")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_LOGIN_ID"))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 아이디입니다."));
    }
}
