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
 * 400, 404, 500 오류가 공통 봉투(ApiResponse) 형식으로 반환되는지 확인한다. errorId는 500에만 있다.
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
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.message").exists());
    }

    // 존재하지 않는 API 요청 테스트
    @Test
    void unknownApiReturns404() throws Exception {

        mockMvc.perform(get("/api/not-existing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.error.errorId").doesNotExist());
    }

    // 예상하지 못한 서버 오류 테스트
    @Test
    void unexpectedErrorReturns500() throws Exception {

        mockMvc.perform(get("/api/test-errors/server"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SERVER_ERROR"))
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.error.errorId").isString());
    }
}
