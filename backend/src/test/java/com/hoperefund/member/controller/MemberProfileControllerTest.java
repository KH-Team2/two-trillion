package com.hoperefund.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.common.exception.GlobalExceptionHandler;
import com.hoperefund.common.service.TokenService.TokenInfo;
import com.hoperefund.member.dto.MemberProfileDTO;
import com.hoperefund.member.dto.MemberProfileResponseDTO;
import com.hoperefund.member.service.MemberProfileService;
import com.hoperefund.member.service.MemberWithdrawalService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 회원정보 · 자격 정보 수정 API 테스트 (F-01-04) · 회원 탈퇴 API 테스트 (F-01-05)
 * 요청 형식 검증 · 응답 봉투 · 로그인한 회원번호 전달 · Service 오류가 그대로 나가는지 확인한다.
 * 토큰 없이 부르면 401 인 것은 SecurityConfig 의 /api/** 규칙 (SecurityConfigTest).
 */
@WebMvcTest(MemberProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MemberProfileControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String URL = "/api/members/me";
    private static final String PASSWORD_URL = "/api/members/me/password";
    private static final String WITHDRAWAL_URL = "/api/members/me/withdrawal";
    private static final String WITHDRAWAL_BODY = "{\"password\": \"password1!\"}";
    private static final TokenInfo TOKEN =
            new TokenInfo(MEMBER_ID, "test1234", "USER", "token-1", Instant.now().plusSeconds(60));
    private static final String PROFILE_BODY =
            """
            {"gender": "M", "militaryMonths": 18, "hireDate": "2023-03-01",
             "companySize": "SME", "homelessHeadYn": "Y"}
            """;
    // 입사일 · 회사 규모를 비운 본문
    private static final String EMPTY_OPTIONAL_BODY =
            """
            {"gender": "F", "militaryMonths": 0, "hireDate": null,
             "companySize": null, "homelessHeadYn": "N"}
            """;
    private static final String PASSWORD_BODY =
            """
            {"currentPassword": "oldPassword1!", "newPassword": "newPassword1!",
             "newPasswordConfirm": "newPassword1!"}
            """;

    @Autowired private MockMvc mockMvc;

    // 로그인한 회원으로 JSON 본문 PUT 요청
    private ResultActions putJson(String url, String body) throws Exception {
        MockHttpServletRequestBuilder request =
                put(url).with(loginAs(MEMBER_ID)).contentType(MediaType.APPLICATION_JSON);
        return mockMvc.perform(request.content(body));
    }

    @MockitoBean private MemberProfileService memberProfileService;
    @MockitoBean private MemberWithdrawalService memberWithdrawalService;

    // 로그인한 회원으로 요청 (JWT 필터가 하는 일을 대신: principal = 회원번호)
    private static RequestPostProcessor loginAs(long memberId) {
        return request -> {
            UsernamePasswordAuthenticationToken login =
                    new UsernamePasswordAuthenticationToken(memberId, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(login);
            return request;
        };
    }

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    // Service 가 돌려줄 회원정보 (이메일은 가린 값)
    private static MemberProfileResponseDTO profile() {
        MemberProfileDTO row = new MemberProfileDTO();
        row.setLoginId("test1234");
        row.setBirthDate(LocalDate.of(1999, 5, 20));
        row.setGender("M");
        row.setMilitaryMonths(18);
        row.setHireDate(LocalDate.of(2023, 3, 1));
        row.setCompanySize("SME");
        row.setHomelessHeadYn("Y");
        row.setUpdatedAt(LocalDateTime.of(2026, 11, 21, 10, 0));
        return MemberProfileResponseDTO.from(row, "ho***@example.com");
    }

    // 조회 → 200 · 가린 이메일 · 권한 · 상태 · 비밀번호는 없음
    @Test
    void getProfileReturns200() throws Exception {
        given(memberProfileService.getProfile(MEMBER_ID)).willReturn(profile());

        mockMvc.perform(get(URL).with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.loginId").value("test1234"))
                .andExpect(jsonPath("$.data.maskedEmail").value("ho***@example.com"))
                .andExpect(jsonPath("$.data.hireDate").value("2023-03-01"))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.role").doesNotExist())
                .andExpect(jsonPath("$.data.status").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    // 수정 → 200 · 토큰의 회원번호로 저장
    @Test
    void updateProfileReturns200() throws Exception {
        given(memberProfileService.updateProfile(eq(MEMBER_ID), any())).willReturn(profile());

        putJson(URL, PROFILE_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.militaryMonths").value(18))
                .andExpect(jsonPath("$.data.companySize").value("SME"));
        verify(memberProfileService).updateProfile(eq(MEMBER_ID), any());
    }

    // 군 복무 음수 → 400 「0 이상으로 입력해 주세요」 · Service 안 부름
    @Test
    void negativeMilitaryMonthsReturns400() throws Exception {
        putJson(URL, PROFILE_BODY.replace("18", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.message").value("0 이상으로 입력해 주세요"));
        verify(memberProfileService, never()).updateProfile(anyLong(), any());
    }

    // 성별 비움 → 400
    @Test
    void blankGenderReturns400() throws Exception {
        putJson(URL, PROFILE_BODY.replace("\"M\"", "\"\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("성별을 선택해 주세요"));
    }

    // 회사 규모 · 무주택 값이 정해진 값이 아님 → 400
    @Test
    void invalidCodesReturn400() throws Exception {
        putJson(URL, PROFILE_BODY.replace("SME", "HUGE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("회사 규모를 선택해 주세요"));
        putJson(URL, PROFILE_BODY.replace("\"Y\"", "\"X\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("무주택 세대주 여부를 선택해 주세요"));
    }

    // 입사일이 내일(아직 오지 않은 날짜) → 400 · Service 안 부름
    @Test
    void futureHireDateReturns400() throws Exception {
        String tomorrow = LocalDate.now().plusDays(1).toString();
        putJson(URL, PROFILE_BODY.replace("2023-03-01", tomorrow))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.message").value("입사일은 오늘 이후로 입력할 수 없어요"));
        verify(memberProfileService, never()).updateProfile(anyLong(), any());
    }

    // 입사일이 오늘이면 저장됨 (오늘 입사도 가능)
    @Test
    void todayHireDateReturns200() throws Exception {
        given(memberProfileService.updateProfile(eq(MEMBER_ID), any())).willReturn(profile());

        putJson(URL, PROFILE_BODY.replace("2023-03-01", LocalDate.now().toString()))
                .andExpect(status().isOk());
    }

    // 입사일 · 회사 규모는 비워도 됨 (null 로 저장)
    @Test
    void optionalFieldsCanBeNull() throws Exception {
        given(memberProfileService.updateProfile(eq(MEMBER_ID), any())).willReturn(profile());

        putJson(URL, EMPTY_OPTIONAL_BODY).andExpect(status().isOk());
    }

    // 비밀번호 변경 → 200 · data null
    @Test
    void changePasswordReturns200() throws Exception {
        putJson(PASSWORD_URL, PASSWORD_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(memberProfileService).changePassword(eq(MEMBER_ID), any());
    }

    // 새 비밀번호 8자 미만 → 400 · Service 안 부름
    @Test
    void shortNewPasswordReturns400() throws Exception {
        putJson(PASSWORD_URL, PASSWORD_BODY.replace("newPassword1!", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("비밀번호는 8자 이상 입력해 주세요"));
        verify(memberProfileService, never()).changePassword(anyLong(), any());
    }

    // 현재 비밀번호 틀림 → Service 오류가 그대로 400
    @Test
    void wrongCurrentPasswordReturns400() throws Exception {
        String message = "현재 비밀번호가 맞지 않아요";
        BusinessException error =
                new BusinessException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_MISMATCH", message);
        willThrow(error).given(memberProfileService).changePassword(eq(MEMBER_ID), any());

        putJson(PASSWORD_URL, PASSWORD_BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CURRENT_PASSWORD_MISMATCH"))
                .andExpect(jsonPath("$.error.message").value("현재 비밀번호가 맞지 않아요"));
    }

    // ---------- 회원 탈퇴 (F-01-05) ----------

    // 로그인한 회원 + 토큰 정보(JWT 필터가 details 에 넣는 값)로 탈퇴 요청
    private ResultActions postWithdrawal(String body) throws Exception {
        RequestPostProcessor login =
                request -> {
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(MEMBER_ID, null, List.of());
                    auth.setDetails(TOKEN);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    // Controller 의 Authentication 매개변수는 요청의 principal 에서 온다 (필터를 끈 테스트라 직접 넣음)
                    request.setUserPrincipal(auth);
                    return request;
                };
        MockHttpServletRequestBuilder request =
                post(WITHDRAWAL_URL).with(login).contentType(MediaType.APPLICATION_JSON);
        return mockMvc.perform(request.content(body));
    }

    // 탈퇴 → 200 · data null · 토큰의 회원번호 · 토큰 정보를 Service 로
    @Test
    void withdrawReturns200() throws Exception {
        postWithdrawal(WITHDRAWAL_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(memberWithdrawalService).withdraw(eq(MEMBER_ID), any(), eq(TOKEN));
    }

    // 비밀번호 비움 → 400 · Service 안 부름
    @Test
    void withdrawBlankPasswordReturns400() throws Exception {
        postWithdrawal("{\"password\": \"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.message").value("비밀번호를 입력해 주세요"));
        verify(memberWithdrawalService, never()).withdraw(anyLong(), any(), any());
    }

    // 비밀번호 틀림 → Service 오류가 그대로 400
    @Test
    void withdrawWrongPasswordReturns400() throws Exception {
        String message = "비밀번호가 맞지 않아요";
        BusinessException error =
                new BusinessException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_MISMATCH", message);
        willThrow(error).given(memberWithdrawalService).withdraw(eq(MEMBER_ID), any(), any());

        postWithdrawal(WITHDRAWAL_BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CURRENT_PASSWORD_MISMATCH"))
                .andExpect(jsonPath("$.error.message").value(message));
    }

    // 탈퇴 주소에 GET → 405
    @Test
    void withdrawalGetReturns405() throws Exception {
        mockMvc.perform(get(WITHDRAWAL_URL).with(loginAs(MEMBER_ID)))
                .andExpect(status().isMethodNotAllowed());
    }
}
