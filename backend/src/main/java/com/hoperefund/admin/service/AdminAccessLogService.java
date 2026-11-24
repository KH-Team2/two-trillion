package com.hoperefund.admin.service;

import com.hoperefund.admin.dto.AccessLogResponseDto;
import com.hoperefund.admin.mapper.AdminAccessLogMapper;
import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.common.dto.PageMeta;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * F-21: 접근 기록 조회 비즈니스 로직
 */
@Service
@RequiredArgsConstructor
public class AdminAccessLogService {

    private final AdminAccessLogMapper adminAccessLogMapper;

    /**
     * 1-based page 번호를 DB 오프셋으로 변환해 목록과 전체 건수를 조회하고
     * 공통 응답 봉투(ApiResponse)와 페이지 메타정보(PageMeta)로 반환
     */
    public ApiResponse<List<AccessLogResponseDto>> getAccessLogs(
            LocalDate startDate, LocalDate endDate, String ipAddress, int page, int size) {
        int offset = (page - 1) * size;
        List<AccessLogResponseDto> list =
                adminAccessLogMapper.selectAccessLogs(startDate, endDate, ipAddress, offset, size);
        long total = adminAccessLogMapper.countAccessLogs(startDate, endDate, ipAddress);
        return ApiResponse.ok(list, PageMeta.of(page, size, (int) total));
    }
}
