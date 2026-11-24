package com.hoperefund.admin.mapper;

import com.hoperefund.admin.dto.AccessLogResponseDto;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * F-21: 접근 기록 조회 매퍼 인터페이스
 */
@Mapper
public interface AdminAccessLogMapper {

    /**
     * 검색 조건(시작일, 종료일, IP) 및 페이징(offset, size)으로 접근 기록 목록 조회
     */
    List<AccessLogResponseDto> selectAccessLogs(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("ipAddress") String ipAddress,
            @Param("offset") int offset,
            @Param("size") int size);

    /**
     * 검색 조건에 일치하는 전체 접근 기록 건수 조회 (PageMeta total 산출용)
     */
    long countAccessLogs(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("ipAddress") String ipAddress);
}
