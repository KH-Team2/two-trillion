package com.hoperefund.admin.controller;

import com.hoperefund.admin.dto.AccessLogResponseDto;
import com.hoperefund.admin.service.AdminAccessLogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/access-logs")
@RequiredArgsConstructor
public class AdminAccessLogController {
    /* F-21 (접근 기록 조회 및 실패 횟수 모니터링) */
    private final AdminAccessLogService adminAccessLogService;

    /**
     * 
     * F-21: 접근 기록 및 로그인 실패 모니터링 조회 (GET /api/admin/access-logs)
     * - 쿼리 파라미터(선택): startDate, endDate, ipAddress, page, size
     * 
     */
    @GetMapping
    public ResponseEntity<List<AccessLogResponseDto>> getAccessLogs(
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "ipAddress", required = false) String ipAddress,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        List<AccessLogResponseDto> logs =
                adminAccessLogService.getAccessLogs(startDate, endDate, ipAddress, page, size);
        return ResponseEntity.ok(logs);
    }
}
