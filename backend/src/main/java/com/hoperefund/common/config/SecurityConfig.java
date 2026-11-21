package com.hoperefund.common.config;

import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.common.service.TokenService;
import com.hoperefund.common.service.TokenService.TokenInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * 로그인 · 권한 검사 설정 (F-01-02 · decisions/0004).
 *
 * <pre>
 * 요청 → JwtAuthenticationFilter (Authorization: Bearer 토큰 검사 → 로그인 정보 넣기)
 *      → 주소별 규칙 확인 (위에서부터 처음 맞는 규칙 사용)
 *         ├ 로그인 필요한데 정보 없음(토큰 없음 · 위조 · 만료 · 로그아웃됨) → 401 UNAUTHORIZED
 *         ├ 로그인했지만 권한 없음(USER가 /api/admin/**)               → 403 FORBIDDEN
 *         └ 통과 → Controller ({@code @AuthenticationPrincipal Long memberId}로 회원번호 받음)
 * </pre>
 *
 * 세션 · 쿠키를 쓰지 않고 토큰을 헤더로만 받으므로 CSRF 보호는 끈다 (브라우저가 헤더를 자동으로 붙이지 않음).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JsonMapper jsonMapper; // 401 · 403 응답 본문(JSON)을 만든다

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, TokenService tokenService)
            throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(tokenService);

        http
                // ① 쓰지 않는 기본 기능 끄기 (로그아웃은 POST /api/auth/logout · MemberController)
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults()) // CorsConfig 설정을 Security 단계에도 적용

                // ② 세션을 만들지 않는다 → 요청마다 토큰으로만 판단
                .sessionManagement(SecurityConfig::stateless)

                // ③ 주소별 접근 규칙 (authorizeRules)
                .authorizeHttpRequests(SecurityConfig::authorizeRules)

                // ④ 필터 단계 오류는 GlobalExceptionHandler 가 잡지 못하므로 여기서 공통 봉투로 응답
                .exceptionHandling(this::errorHandlers)

                // ⑤ 토큰 필터를 Security 필터 체인에 끼워 넣는다
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static void stateless(SessionManagementConfigurer<HttpSecurity> session) {
        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }

    // 주소별 접근 규칙 — 위에서부터 차례로 검사해 처음 맞는 규칙을 쓴다 (순서가 중요)
    private static void authorizeRules(
            AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        // CORS 사전 요청(OPTIONS)은 토큰 없이 보냄
        auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
        // 로그아웃은 "어떤 토큰을 끊을지" 알아야 해서 /api/auth/** 보다 먼저 적음
        auth.requestMatchers("/api/auth/logout").authenticated();
        // 로그인 전: 가입 · 중복확인 · 이메일 인증 · 로그인 · 아이디 찾기 · 비밀번호 재설정 · 서버 상태 확인
        auth.requestMatchers("/api/auth/**", "/api/health").permitAll();
        auth.requestMatchers("/api/admin/**").hasRole("ADMIN");
        // 나머지 API 는 로그인한 회원만
        auth.requestMatchers("/api/**").authenticated();
        // API 가 아닌 주소 (오류 처리 /error 등)
        auth.anyRequest().permitAll();
    }

    // 401: 로그인 정보 없음 · 403: 권한 없음
    private void errorHandlers(ExceptionHandlingConfigurer<HttpSecurity> handler) {
        handler.authenticationEntryPoint((request, response, e) -> writeUnauthorized(response));
        handler.accessDeniedHandler((request, response, e) -> writeForbidden(response));
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        writeError(response, SC_UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요해요");
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        writeError(response, SC_FORBIDDEN, "FORBIDDEN", "접근 권한이 없어요");
    }

    // 401 · 403 응답을 다른 API와 같은 봉투 {"success": false, "error": {"code", "message"}}로 보냄
    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getWriter(), ApiResponse.fail(code, message));
    }

    /**
     * 요청마다 Authorization: Bearer 토큰을 검사해 로그인 정보를 넣는 필터.
     * 토큰이 없거나 틀리면 아무것도 넣지 않는다 → 로그인이 필요한 주소면 위 규칙이 401로 응답한다.
     * Bean으로 등록하지 않는다 (등록하면 Security 밖에서 한 번 더 실행됨).
     */
    static class JwtAuthenticationFilter extends OncePerRequestFilter {

        private static final String BEARER_PREFIX = TokenService.TOKEN_TYPE + " ";

        private final TokenService tokenService;

        JwtAuthenticationFilter(TokenService tokenService) {
            this.tokenService = tokenService;
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {

            // 요청 헤더 예: Authorization: Bearer eyJhbGciOi...
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith(BEARER_PREFIX)) {
                String token = header.substring(BEARER_PREFIX.length());
                tokenService.verify(token).ifPresent(JwtAuthenticationFilter::login);
            }
            chain.doFilter(request, response); // 다음 필터로 넘긴다
        }

        // 맞는 토큰이면 로그인 정보를 넣는다 · principal = 회원번호 · 권한 = ROLE_USER / ROLE_ADMIN
        private static void login(TokenInfo info) {
            SimpleGrantedAuthority role = new SimpleGrantedAuthority("ROLE_" + info.role());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(info.memberId(), null, List.of(role));
            authentication.setDetails(info); // 로그아웃 때 토큰 번호 · 만료 시각 사용
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }
}
