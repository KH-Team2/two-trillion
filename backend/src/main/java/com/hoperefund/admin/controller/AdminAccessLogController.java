package com.hoperefund.admin.controller;

import com.hoperefund.admin.dto.AccessLogResponseDTO;
import com.hoperefund.admin.service.AdminAccessLogService;
import com.hoperefund.common.dto.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

/**
 * F-21: 관리자 접근 기록 조회 API 컨트롤러
 */
@RestController
@RequestMapping("/api/admin/access-logs")
@RequiredArgsConstructor
public class AdminAccessLogController {

    // 한 페이지당 조회 가능한 최대 건수 제한
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminAccessLogService adminAccessLogService;

    /**
     * 접근 기록 목록 조회 (GET /api/admin/access-logs)
     * - startDate, endDate: ISO 날짜(YYYY-MM-DD) 형식으로 바인딩 (형식 오류 시 400 처리)
     * - page: 1부터 시작하는 페이지 번호 (최소 1 보정)
     * - size: 페이지 크기 (1 ~ 100 범위로 제한)
     */
    @GetMapping
    public ApiResponse<List<AccessLogResponseDTO>> getAccessLogs(
            @RequestParam(value = "startDate", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate,
            @RequestParam(value = "ipAddress", required = false) String ipAddress,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        return adminAccessLogService.getAccessLogs(
                startDate, endDate, ipAddress, safePage, safeSize);
    }
}
