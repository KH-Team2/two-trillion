package com.hoperefund.common.filter;

import com.hoperefund.common.service.AccessLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 주요 동작의 접근 기록(ACCESS_LOG)을 남기는 필터 (F-09-01 · D-14).
 *
 * <pre>
 * 요청 → JwtAuthenticationFilter(로그인 정보) → 이 필터 → 권한 검사(403) → Controller
 *                                              └ 응답이 끝나면(finally) 상태 코드를 보고 기록
 *
 * 기록하는 요청
 *   업로드      POST /api/card-uploads
 *   계산        POST /api/calculations/** · POST /api/simulations/**
 *   관리자 동작  /api/admin/** (조회 포함 · 모든 방식)
 *               단, 관리자 서비스가 성공 기록을 직접 남기는 요청은 성공이면 남기지 않는다 (같은 기록 2줄 방지)
 *               → isRecordedByAdminService 목록 · 실패(FAIL)는 서비스가 안 남기므로 필터가 남긴다
 *   권한 없음    403 이면 주소와 관계없이 DENIED
 * 기록하지 않는 요청
 *   401 (로그인 안 함 · 토큰 만료) · 로그인(LoginService 가 따로 남김) · 그 밖의 주소 · CORS 사전 요청(OPTIONS)
 *
 * 결과: 2xx → SUCCESS · 403 → DENIED · 그 밖의 4xx · 5xx · 처리 중 예외 → FAIL
 * </pre>
 *
 * Bean 으로 등록하지 않는다 (등록하면 Security 밖에서 한 번 더 실행됨 · SecurityConfig 가 직접 만든다).
 */
public class AccessLogFilter extends OncePerRequestFilter {

    private final AccessLogService accessLogService;

    public AccessLogFilter(AccessLogService accessLogService) {
        this.accessLogService = accessLogService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        boolean failed = false; // Controller 에서 처리되지 않은 예외가 올라왔는지
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            failed = true;
            throw e; // 예외는 그대로 넘긴다 (기록만 하고 응답은 바꾸지 않음)
        } finally {
            String result = resultOf(request, response.getStatus(), failed);
            if (result != null) {
                String screen = request.getRequestURI();
                // X-Forwarded-For 는 화면이 마음대로 바꿀 수 있어 쓰지 않는다 (로그인 기록과 같은 기준)
                String ipAddress = request.getRemoteAddr();
                accessLogService.record(loginMemberId(), screen, ipAddress, result);
            }
        }
    }

    // 남길 결과 값 · 기록하지 않을 요청이면 null
    static String resultOf(HttpServletRequest request, int status, boolean failed) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return null;
        }
        if (status == HttpServletResponse.SC_UNAUTHORIZED) {
            return null; // 401 은 남기지 않는다 (토큰 만료 때 화면이 여러 주소를 한꺼번에 불러 의미 없는 기록이 쌓임)
        }
        if (status == HttpServletResponse.SC_FORBIDDEN) {
            return AccessLogService.DENIED;
        }
        if (!isTarget(request.getMethod(), request.getRequestURI())) {
            return null;
        }
        if (failed || status >= 400) {
            return AccessLogService.FAIL;
        }
        if (isRecordedByAdminService(request.getMethod(), request.getRequestURI())) {
            return null; // 관리자 서비스가 이미 SUCCESS 로 남김
        }
        return AccessLogService.SUCCESS;
    }

    // 관리자 서비스가 성공했을 때 ACCESS_LOG 를 직접 남기는 요청 (develop 의 관리자 기능 · 11/23 고은)
    //   AdminAccessLogService · AdminMemberService
    //   AdminPredictionService · AdminTaxCriteriaService
    // 관리자 서비스에 기록을 더하거나 빼면 이 목록도 같이 고친다
    static boolean isRecordedByAdminService(String method, String uri) {
        if (HttpMethod.GET.matches(method)) {
            return uri.equals("/api/admin/access-logs")
                    || uri.equals("/api/admin/members")
                    || uri.equals("/api/admin/predictions");
        }
        if (HttpMethod.PUT.matches(method)) {
            return uri.matches("/api/admin/members/[^/]+/role")
                    || uri.equals("/api/admin/tax-criteria");
        }
        if (HttpMethod.POST.matches(method)) {
            return uri.equals("/api/admin/tax-criteria")
                    || uri.equals("/api/admin/tax-criteria/years");
        }
        return false;
    }

    // 기록 대상 주소인지 (D-14: 로그인 · 업로드 · 계산 · 관리자 · 로그인은 LoginService 가 남김)
    static boolean isTarget(String method, String uri) {
        if (uri.equals("/api/admin") || uri.startsWith("/api/admin/")) {
            return true;
        }
        if (!HttpMethod.POST.matches(method)) {
            return false;
        }
        return uri.equals("/api/card-uploads")
                || uri.startsWith("/api/calculations/")
                || uri.startsWith("/api/simulations/");
    }

    // 로그인한 회원번호 (JwtAuthenticationFilter 가 넣은 principal) · 로그인 전이면 null
    private static Long loginMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long memberId) {
            return memberId;
        }
        return null;
    }
}
