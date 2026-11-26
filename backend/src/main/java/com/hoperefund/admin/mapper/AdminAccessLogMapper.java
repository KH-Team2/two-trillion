package com.hoperefund.admin.mapper;

import com.hoperefund.admin.dto.AccessLogResponseDto;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** ACCESS_LOG(접근 기록) 조회 (관리자). SQL은 resources/mapper/admin/AdminAccessLogMapper.xml 에 있다. */
@Mapper
public interface AdminAccessLogMapper {
    // F -21 조건별(기간, IP) 접근 기록 조회 및 페이징

    // [수정 11/20] String startDate · endDate → LocalDate startDate · endDateExclusive
    //   이유(PR #45 리뷰): 날짜를 글자로 받으면 2026-11-32처럼 잘못된 값이 SQL TO_DATE까지 가서 DB 오류(500)
    //   → 컨트롤러에서 LocalDate로 받아 형식 오류는 400으로 막고, SQL에는 날짜 그대로 넘김
    //   endDateExclusive = 끝 날짜 + 1일 (그 날 23:59:59까지 포함하려고 「다음 날 0시 미만」으로 비교 · Service에서 계산)
    List<AccessLogResponseDto> selectAccessLogs(
            @Param("startDate") LocalDate startDate,
            @Param("endDateExclusive") LocalDate endDateExclusive,
            @Param("ipAddress") String ipAddress,
            @Param("offset") int offset,
            @Param("size") int size);

    // [추가 11/20] 검색 조건에 맞는 전체 기록 수 · 이유(PR #45 리뷰 ③): 전체 개수가 없어 화면이 총 페이지 수를 못 그림
    int countAccessLogs(
            @Param("startDate") LocalDate startDate,
            @Param("endDateExclusive") LocalDate endDateExclusive,
            @Param("ipAddress") String ipAddress);
}
