package com.hoperefund.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** ACCESS_LOG(접속 기록) 저장. SQL 은 resources/mapper/common/AccessLogMapper.xml 에 있다. */
@Mapper
public interface AccessLogMapper {

    // 접속 기록 1건 저장. memberId 는 없는 아이디로 로그인에 실패하면 null
    int insertAccessLog(
            @Param("memberId") Long memberId,
            @Param("screen") String screen,
            @Param("ipAddress") String ipAddress,
            @Param("result") String result);
}
