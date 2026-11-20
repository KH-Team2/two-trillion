package com.hoperefund.admin.mapper;

import com.hoperefund.admin.dto.AccessLogResponseDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminAccessLogMapper {
    // F -21 조건별(기간, IP) 접근 기록 조회 및 페이징

    List<AccessLogResponseDto> selectAccessLogs(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("ipAddress") String ipAddress,
            @Param("offset") int offset,
            @Param("size") int size);
}
