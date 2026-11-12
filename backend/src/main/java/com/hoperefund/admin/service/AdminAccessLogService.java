package com.hoperefund.admin.service;

import com.hoperefund.admin.dto.AccessLogResponseDto;
import com.hoperefund.admin.mapper.AdminAccessLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAccessLogService {
	/* F-21 비즈니스 로직 */
	
	private final AdminAccessLogMapper adminAccessLogMapper;

	// 접근 기록 목록 조회 비즈니스 로직
	public List<AccessLogResponseDto> getAccessLogs(String startDate, String endDate, String ipAddress, int page, int size) {
        int offset = page * size;
        return adminAccessLogMapper.selectAccessLogs(startDate, endDate, ipAddress, offset, size);
    }
}
