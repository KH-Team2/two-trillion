package com.hoperefund.admin.mapper;

import com.hoperefund.admin.dto.AdminPredictionResponseDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 관리자 예측 결과 조회 SQL 연결 (F-08-02 ②) · [신규 11/26 고은]
 *
 * <p>SQL은 resources/mapper/admin/AdminPredictionMapper.xml에 있어요. 메서드 이름 = XML의 id.
 *
 * <p>- @Param("이름"): XML에서 #{이름}으로 꺼내 쓸 이름표예요.
 */
@Mapper
public interface AdminPredictionMapper {

    /** 검색 조건에 맞는 예측 결과 한 쪽 (최신순) */
    List<AdminPredictionResponseDto> selectPredictions(
            @Param("keyword") String keyword,
            @Param("taxYear") Integer taxYear,
            @Param("kind") String kind,
            @Param("offset") int offset,
            @Param("size") int size);

    /** 검색 조건에 맞는 전체 건수 (화면의 「전체 N건 · 쪽 수」용) */
    int countPredictions(
            @Param("keyword") String keyword,
            @Param("taxYear") Integer taxYear,
            @Param("kind") String kind);
}
