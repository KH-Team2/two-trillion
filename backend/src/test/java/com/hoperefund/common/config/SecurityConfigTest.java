package com.hoperefund.common.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hoperefund.common.controller.HealthController;
import com.hoperefund.common.exception.GlobalExceptionHandler;
import com.hoperefund.common.service.TokenService;
import com.hoperefund.common.service.TokenService.TokenInfo;
import com.hoperefund.income.controller.IncomeController;
import com.hoperefund.income.service.IncomeService;
import com.hoperefund.member.controller.MemberController;
import com.hoperefund.member.dto.LoginRequestDTO;
import com.hoperefund.member.dto.LoginResponseDTO;
import com.hoperefund.member.service.LoginService;
import com.hoperefund.member.service.MemberService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 로그인 · 권한 검사 설정 테스트 (실제 Security 필터를 켜고 확인)
 * 열린 주소 · 401 UNAUTHORIZED · 403 FORBIDDEN 봉투 · 로그아웃 후 같은 토큰 거부 · CORS 사전 요청을 확인한다.
 * [수정 11/17] 토큰의 회원번호가 Controller(@AuthenticationPrincipal)까지 전달되는지 · 회원 API 6곳 401 확인 추가
 */
@WebMvcTest({MemberController.class, HealthController.class, IncomeController.class})
@Import({SecurityConfig.class, CorsConfig.class, TokenService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-must-be-at-least-32-bytes")
class SecurityConfigTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;

    @MockitoBean private MemberService memberService;
    @MockitoBean private LoginService loginService;
    @MockitoBean private IncomeService incomeService;

    private String bearer(String role) {
        return "Bearer " + tokenService.issue(3L, "hope_user", role).token();
    }

    // 로그인 전 주소는 토큰 없이 통과 (서버 상태 확인 · 로그인)
    @Test
    void publicUrlsNeedNoToken() throws Exception {

        mockMvc.perform(get("/api/health")).andExpect(status().isOk());

        given(loginService.login(any(LoginRequestDTO.class), anyString()))
                .willReturn(
                        new LoginResponseDTO(
                                "token", "Bearer", LocalDateTime.now(), "hope_user", "USER"));
        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"loginId\": \"hope_user\", \"password\": \"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // 로그인이 필요한 주소에 토큰이 없거나 틀리면 401 UNAUTHORIZED (공통 봉투)
    @Test
    void protectedUrlWithoutValidTokenReturns401() throws Exception {

        mockMvc.perform(get("/api/incomes/2026"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("로그인이 필요해요"));

        mockMvc.perform(
                        get("/api/incomes/2026")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        // 로그아웃은 /api/auth/** 안에 있지만 로그인이 필요함
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // 토큰이 맞으면 통과 → Controller가 토큰 안의 회원번호(3)를 받음 (헤더 · 주소로 받지 않음)
    // [수정 11/17] X-Member-Id 헤더 → @AuthenticationPrincipal 전환 확인 (전에는 Controller가 없어 404만 확인)
    @Test
    void validTokenPassesMemberIdToController() throws Exception {

        mockMvc.perform(get("/api/incomes/2026").header(HttpHeaders.AUTHORIZATION, bearer("USER")))
                .andExpect(status().isOk());
        verify(incomeService).getIncome(3L, 2026);
    }

    // 회원 데이터 API는 모두 토큰이 있어야 함 (예전 X-Member-Id 헤더만 보내면 401)
    @Test
    void memberApisNeedToken() throws Exception {

        String[] urls = {
            "/api/incomes/2026",
            "/api/calculations/2026",
            "/api/benefits/2026",
            "/api/predictions?year=2026",
            "/api/predictions/6",
            "/api/group-stats/2026"
        };
        for (String url : urls) {
            mockMvc.perform(get(url).header("X-Member-Id", "81"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }
        mockMvc.perform(post("/api/simulations/2026").header("X-Member-Id", "81"))
                .andExpect(status().isUnauthorized());
    }

    // 일반 회원이 관리자 API → 403 FORBIDDEN · 관리자는 통과
    @Test
    void adminUrlNeedsAdminRole() throws Exception {

        mockMvc.perform(get("/api/admin/members").header(HttpHeaders.AUTHORIZATION, bearer("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("접근 권한이 없어요"));

        mockMvc.perform(
                        get("/api/admin/members")
                                .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
                .andExpect(status().isNotFound());
    }

    // 로그아웃 → 200 + data: null · 같은 토큰으로 다시 요청하면 401
    @Test
    void logoutRevokesTokenForNextRequests() throws Exception {

        willAnswer(
                        invocation -> {
                            tokenService.logout(invocation.getArgument(0, TokenInfo.class));
                            return null;
                        })
                .given(loginService)
                .logout(any(TokenInfo.class));
        String token = bearer("USER");

        mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/incomes/2026").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    // CORS 사전 요청(OPTIONS)은 토큰 없이 통과하고 Authorization 헤더를 허용한다
    @Test
    void corsPreflightIsAllowedWithoutToken() throws Exception {

        mockMvc.perform(
                        options("/api/incomes/2026")
                                .header("Origin", "http://localhost:5173")
                                .header("Access-Control-Request-Method", "GET")
                                .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
