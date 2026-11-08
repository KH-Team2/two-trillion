package com.hoperefund.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 공통 API 오류 응답 테스트
 * 400, 404, 500 오류가 팀 규칙의 JSON 형식으로 반환되는지 확인한다.
 * TODO: 인증·권한 및 업무 오류 처리 구현 후 테스트 추가
 */
@WebMvcTest(TestErrorController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired private MockMvc mockMvc;

    // 잘못된 JSON 요청 테스트
    @Test
    void invalidJsonReturns400() throws Exception {

        mockMvc.perform(
                        post("/api/test-errors/json")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{invalid-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION"))
                .andExpect(jsonPath("$.message").exists());
    }

    // 존재하지 않는 API 요청 테스트
    @Test
    void unknownApiReturns404() throws Exception {

        mockMvc.perform(get("/api/not-existing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }

    // 업무 오류 테스트 (서비스가 정한 상태 코드 · 오류 코드 · 문구)
    @Test
    void businessErrorReturnsGivenStatus() throws Exception {

        mockMvc.perform(get("/api/test-errors/business"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("TEST_CONFLICT"))
                .andExpect(jsonPath("$.message").value("테스트 업무 오류"));
    }

    // 예상하지 못한 서버 오류 테스트
    @Test
    void unexpectedErrorReturns500() throws Exception {

        mockMvc.perform(get("/api/test-errors/server"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("SERVER_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }
}
