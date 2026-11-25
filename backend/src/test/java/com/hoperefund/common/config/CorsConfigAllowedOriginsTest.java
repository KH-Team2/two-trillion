package com.hoperefund.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hoperefund.common.controller.HealthController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * [추가 11/25 · 이우진 · TT-179] 허용 화면 주소를 설정 파일(cors.allowed-origins)에서 읽는지
 *
 * <p>배포 때 환경변수 ALLOWED_ORIGINS 에 넣는 것처럼 값을 바꿔 봄 (쉼표로 2개 · 공백 섞임)
 */
@WebMvcTest(HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CorsConfig.class)
@TestPropertySource(
        properties = "cors.allowed-origins=https://app.example.com, http://localhost:5173")
class CorsConfigAllowedOriginsTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("설정에 넣은 운영 주소는 허용")
    void configuredOriginIsAllowed() throws Exception {
        mockMvc.perform(
                        options("/api/health")
                                .header("Origin", "https://app.example.com")
                                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(
                        header().string("Access-Control-Allow-Origin", "https://app.example.com"));
    }

    @Test
    @DisplayName("쉼표 뒤 두 번째 주소도 허용 (앞뒤 공백은 무시)")
    void secondOriginIsAllowed() throws Exception {
        mockMvc.perform(
                        options("/api/health")
                                .header("Origin", "http://localhost:5173")
                                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    @DisplayName("설정에 없는 주소는 거절")
    void otherOriginIsRejected() throws Exception {
        mockMvc.perform(
                        options("/api/health")
                                .header("Origin", "https://evil.example.com")
                                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
