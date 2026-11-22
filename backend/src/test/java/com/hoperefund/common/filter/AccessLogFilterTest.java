package com.hoperefund.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hoperefund.common.service.AccessLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 접근 기록 필터 테스트 (F-09-01)
 * 가짜 요청 · 응답으로 기록 대상 주소 · 결과 값(SUCCESS · FAIL · DENIED) · 401 제외 · 회원번호를 확인한다.
 */
class AccessLogFilterTest {

    private static final String IP = "127.0.0.1";

    private AccessLogService accessLogService;
    private AccessLogFilter filter;

    @BeforeEach
    void setUp() {
        accessLogService = mock(AccessLogService.class);
        filter = new AccessLogFilter(accessLogService);
    }

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    // 그 회원이 로그인한 상태로 만든다 (JwtAuthenticationFilter 가 넣는 것과 같은 모양)
    private static void loginAs(long memberId) {
        List<SimpleGrantedAuthority> roles = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(memberId, null, roles);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // 요청을 보내고, 뒤 단계(Controller 등)가 status 로 응답했다고 가정한다
    private void send(String method, String uri, int status) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(IP);
        FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(status);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }

    // 업로드 · 계산 · 시뮬레이션 성공 → SUCCESS · 로그인한 회원번호와 함께
    @Test
    void mainActionsAreRecordedAsSuccess() throws Exception {

        loginAs(3L);

        send("POST", "/api/card-uploads", 200);
        send("POST", "/api/calculations/2026", 200);
        send("POST", "/api/simulations/2026", 200);

        verify(accessLogService).record(3L, "/api/card-uploads", IP, "SUCCESS");
        verify(accessLogService).record(3L, "/api/calculations/2026", IP, "SUCCESS");
        verify(accessLogService).record(3L, "/api/simulations/2026", IP, "SUCCESS");
    }

    // 업로드 · 계산이 4xx · 5xx 로 끝나면 FAIL
    @Test
    void failedActionsAreRecordedAsFail() throws Exception {

        loginAs(3L);

        send("POST", "/api/card-uploads", 400);
        send("POST", "/api/calculations/2026", 503);

        verify(accessLogService).record(3L, "/api/card-uploads", IP, "FAIL");
        verify(accessLogService).record(3L, "/api/calculations/2026", IP, "FAIL");
    }

    // 관리자 주소는 조회(GET)까지 모두 기록
    @Test
    void adminRequestsAreAllRecorded() throws Exception {

        loginAs(1L);

        send("GET", "/api/admin/members", 200);
        send("PUT", "/api/admin/inquiries/5/answer", 200);
        send("DELETE", "/api/admin/members/9", 404);

        verify(accessLogService).record(1L, "/api/admin/members", IP, "SUCCESS");
        verify(accessLogService).record(1L, "/api/admin/inquiries/5/answer", IP, "SUCCESS");
        verify(accessLogService).record(1L, "/api/admin/members/9", IP, "FAIL");
    }

    // 403 → DENIED
    @Test
    void forbiddenIsRecordedAsDenied() throws Exception {

        loginAs(3L);

        send("GET", "/api/admin/members", 403);

        verify(accessLogService).record(3L, "/api/admin/members", IP, "DENIED");
    }

    // 401(로그인 안 함 · 토큰 만료)은 대상 주소여도 남기지 않는다
    @Test
    void unauthorizedIsNotRecorded() throws Exception {

        send("POST", "/api/calculations/2026", 401);
        send("GET", "/api/admin/members", 401);

        verify(accessLogService, never()).record(any(), any(), any(), any());
    }

    // 대상이 아닌 주소 · 방식은 남기지 않는다 (조회 · 로그인 · CORS 사전 요청)
    @Test
    void otherRequestsAreNotRecorded() throws Exception {

        loginAs(3L);

        send("GET", "/api/calculations/2026", 200); // 저장된 결과 조회
        send("GET", "/api/card-uploads", 200); // 업로드 이력 조회
        send("PUT", "/api/incomes/2026", 200);
        send("POST", "/api/auth/login", 200); // LoginService 가 따로 남김
        send("OPTIONS", "/api/admin/members", 200);

        verify(accessLogService, never()).record(any(), any(), any(), any());
    }

    // Controller 에서 처리되지 않은 예외가 올라오면 FAIL 로 남기고 예외는 그대로 넘긴다
    @Test
    void unhandledExceptionIsRecordedAsFailAndRethrown() {

        loginAs(3L);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/card-uploads");
        request.setRemoteAddr(IP);
        FilterChain chain =
                (req, res) -> {
                    throw new IllegalStateException("처리 중 오류");
                };

        MockHttpServletResponse response = new MockHttpServletResponse();
        Throwable thrown = catchThrowable(() -> filter.doFilter(request, response, chain));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verify(accessLogService).record(3L, "/api/card-uploads", IP, "FAIL");
    }
}
