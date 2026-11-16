package com.hoperefund.statistics.mapper;

import com.hoperefund.statistics.dto.GroupStatDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** GROUP_STAT 조회 (SQL은 resources/mapper/statistics/GroupStatMapper.xml) · 읽기만 해요. */
@Mapper
public interface GroupStatMapper {

    /** 본인 성별·연령대 그룹 1행 (성별 미입력이면 null) · 귀속 연도 이하에서 가장 최근 통계 · 나이는 귀속 연도 − 태어난 해 */
    GroupStatDTO findAgeGroup(@Param("memberId") long memberId, @Param("taxYear") int taxYear);

    /** 본인 총급여 구간 그룹 1행 (그 연도 소득이 없으면 null) · 귀속 연도 이하에서 가장 최근 통계 */
    GroupStatDTO findSalaryGroup(@Param("memberId") long memberId, @Param("taxYear") int taxYear);
}
