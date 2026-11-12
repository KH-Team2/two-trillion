package com.hoperefund.statistics.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.hoperefund.common.exception.GlobalExceptionHandler;
import com.hoperefund.statistics.dto.GroupStatDTO;
import com.hoperefund.statistics.dto.GroupStatResponseDTO;
import com.hoperefund.statistics.service.GroupStatService;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 그룹 비교 통계 API의 요청 · 응답 모양을 확인해요 (서버 · DB 없이 MockMvc). 서비스는 정해 둔 결과만 돌려주는 가짜로 바꿔서, 컨트롤러와 오류
 * 처리(GlobalExceptionHandler)만 봐요.
 */
class GroupStatControllerTest {

    // 가짜 서비스가 마지막으로 받은 회원번호 · 연도
    private long receivedMemberId;
    private int receivedTaxYear;

    /** 정해 둔 결과를 돌려주는 가짜 그룹 통계 서비스 */
    private GroupStatService groupStatService(Supplier<GroupStatResponseDTO> answer) {
        return new GroupStatService(null) {
            @Override
            public GroupStatResponseDTO getMyGroups(long memberId, int taxYear) {
                receivedMemberId = memberId;
                receivedTaxYear = taxYear;
                return answer.get();
            }
        };
    }

    private MockHttpServletResponse call(Object controller, RequestBuilder request)
            throws Exception {
        MockMvc mockMvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private String text(MockHttpServletResponse response) throws Exception {
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    private GroupStatDTO group(String label, long avgDecidedTax) {
        GroupStatDTO group = new GroupStatDTO();
        group.setStatYear(2024);
        group.setGroupLabel(label);
        group.setAvgDecidedTax(avgDecidedTax);
        return group;
    }

    @Test
    @DisplayName("조회하면 200과 두 그룹을 돌려주고 헤더의 회원번호와 주소의 연도를 쓴다")
    void getReturns200AndUsesHeaderMemberAndPathYear() throws Exception {
        GroupStatController controller =
                new GroupStatController(
                        groupStatService(
                                () ->
                                        new GroupStatResponseDTO(
                                                2026,
                                                group("여성 40대", 2_274_940L),
                                                group("4천만 이하", 394_361L))));
        MockHttpServletResponse res =
                call(controller, get("/api/group-stats/2026").header("X-Member-Id", "81"));

        assertEquals(200, res.getStatus());
        // 공통 봉투: 결과는 data 안에
        assertTrue(text(res).startsWith("{\"success\":true,\"data\":{"));
        assertTrue(text(res).contains("\"error\":null"));
        assertTrue(text(res).contains("\"groupLabel\":\"여성 40대\""));
        assertTrue(text(res).contains("\"avgDecidedTax\":394361"));
        assertTrue(text(res).contains("\"statYear\":2024"));
        assertEquals(81L, receivedMemberId);
        assertEquals(2026, receivedTaxYear);
    }

    @Test
    @DisplayName("못 찾은 그룹은 칸을 빼지 않고 null로 돌려준다")
    void getReturnsNullForMissingGroup() throws Exception {
        GroupStatController controller =
                new GroupStatController(
                        groupStatService(
                                () ->
                                        new GroupStatResponseDTO(
                                                2026, group("여성 40대", 2_274_940L), null)));
        MockHttpServletResponse res =
                call(controller, get("/api/group-stats/2026").header("X-Member-Id", "81"));

        assertEquals(200, res.getStatus());
        assertTrue(text(res).contains("\"salaryGroup\":null"));
        assertTrue(text(res).contains("\"groupLabel\":\"여성 40대\""));
    }

    @Test
    @DisplayName("회원번호 헤더가 없으면 401")
    void getReturns401WhenMemberHeaderMissing() throws Exception {
        GroupStatController controller =
                new GroupStatController(
                        groupStatService(() -> new GroupStatResponseDTO(2026, null, null)));
        MockHttpServletResponse res = call(controller, get("/api/group-stats/2026"));

        assertEquals(401, res.getStatus());
        assertTrue(text(res).contains("\"code\":\"UNAUTHORIZED\""));
        assertFalse(text(res).contains("errorId")); // 사용자 쪽 오류엔 추적 번호 없음
    }
}
