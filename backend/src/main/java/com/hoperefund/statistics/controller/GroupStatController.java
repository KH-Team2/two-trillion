package com.hoperefund.statistics.controller;

import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.statistics.dto.GroupStatResponseDTO;
import com.hoperefund.statistics.service.GroupStatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 그룹 비교 통계 API (F-06-02 · 「나와 비슷한 사람들은」) TODO(로그인 연결 후): 회원번호를 X-Member-Id 헤더 대신 로그인 정보(Spring
 * Security)에서 꺼내기
 */
@RestController
@RequestMapping("/api/group-stats")
public class GroupStatController {

    private final GroupStatService groupStatService;

    public GroupStatController(GroupStatService groupStatService) {
        this.groupStatService = groupStatService;
    }

    /** 조회: GET /api/group-stats/2026 (본인 성별·연령대 그룹 + 총급여 구간 그룹의 국세통계 평균 · 못 찾은 그룹은 null) */
    @GetMapping("/{taxYear}")
    public ResponseEntity<ApiResponse<GroupStatResponseDTO>> getMyGroups(
            @RequestHeader("X-Member-Id") long memberId, @PathVariable("taxYear") int taxYear) {
        return ResponseEntity.ok(ApiResponse.ok(groupStatService.getMyGroups(memberId, taxYear)));
    }
}
