package com.hoperefund.admin.mapper;

<<<<<<< HEAD
import com.hoperefund.admin.dto.AccessLogResponseDTO;
=======
import com.hoperefund.admin.dto.AccessLogResponseDto;
>>>>>>> aad4652af0ed7a61c0d2e67402c6a605f34e7053
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

<<<<<<< HEAD
/**
 * F-21: 접근 기록 조회 매퍼 인터페이스
 */
@Mapper
public interface AdminAccessLogMapper {

    /**
     * 검색 조건(시작일, 종료일, IP) 및 페이징(offset, size)으로 접근 기록 목록 조회
     */
    List<AccessLogResponseDTO> selectAccessLogs(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
=======
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
>>>>>>> aad4652af0ed7a61c0d2e67402c6a605f34e7053
            @Param("ipAddress") String ipAddress,
            @Param("offset") int offset,
            @Param("size") int size);

<<<<<<< HEAD
    /**
     * 검색 조건에 일치하는 전체 접근 기록 건수 조회 (PageMeta total 산출용)
     */
    long countAccessLogs(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
=======
    // [추가 11/20] 검색 조건에 맞는 전체 기록 수 · 이유(PR #45 리뷰 ③): 전체 개수가 없어 화면이 총 페이지 수를 못 그림
    int countAccessLogs(
            @Param("startDate") LocalDate startDate,
            @Param("endDateExclusive") LocalDate endDateExclusive,
>>>>>>> aad4652af0ed7a61c0d2e67402c6a605f34e7053
            @Param("ipAddress") String ipAddress);
}
