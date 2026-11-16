package com.hoperefund.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 접속 기록(ACCESS_LOG) 저장 Mapper (AGENTS.md: ACCESS_LOG는 common/ 담당)
 * SQL은 resources/mapper/common/AccessLogMapper.xml에 작성한다.
 */
@Mapper
public interface AccessLogMapper {

    // 접속 기록 저장 (LOG_ID = MAX + 1 · 회원번호는 없는 아이디로 로그인 실패하면 null)
    int insertAccessLog(
            @Param("memberId") Long memberId,
            @Param("screen") String screen,
            @Param("ipAddress") String ipAddress,
            @Param("result") String result);
}
