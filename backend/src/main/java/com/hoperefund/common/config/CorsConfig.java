package com.hoperefund.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * React 프론트엔드와 Spring Boot 간 CORS 설정
 * 개발 환경의 React 서버에서 API 요청을 허용한다.
 * TODO: Spring Security 연동 시 CORS 정책 통합 검토
 * [수정 11/25 · 이우진 · TT-179] 허용 출처는 application.yml 의 cors.allowed-origins 에서 읽음
 * (기본값 http://localhost:5173 · 배포 때는 환경변수 ALLOWED_ORIGINS 에 운영 화면 주소)
 */
@Configuration // Spring Boot 설정 클래스 등록
public class CorsConfig implements WebMvcConfigurer {

    // 쉼표로 여러 개를 넣으면 배열로 나눠서 들어옴 (예: https://a.com,https://b.com)
    private final String[] allowedOrigins;

    public CorsConfig(@Value("${cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {

        registry.addMapping("/api/**") // /api/로 시작하는 API에 적용
                .allowedOrigins(allowedOrigins) // 요청을 허용할 React 주소 (설정 파일)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 허용할 HTTP 요청 방식
                // 브라우저에서 전송할 수 있는 요청 헤더 (Authorization: 로그인 토큰)
                // [수정 11/17] X-Member-Id 삭제 · 회원번호는 로그인 토큰에서 꺼냄
                .allowedHeaders("Content-Type", "Authorization")
                .allowCredentials(true) // 	쿠키 등 인증 정보가 포함된 요청 허용
                .maxAge(3600); // 	CORS 사전 요청 결과를 일정 시간 캐시
    }
}
