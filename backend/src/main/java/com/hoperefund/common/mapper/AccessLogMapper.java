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

    // [추가 11/24 고은] D-16 IP 기준 로그인 제한 (F-09-01)
    // 이유: 한 IP가 짧은 시간에 로그인을 마구 틀리면(아이디·비밀번호 무작위 대입 공격) 그 IP를 잠시 막기 위해
    // 최근 blockHours 시간 안에 "windowMinutes 분 동안 실패 failLimit 번 이상"이 한 번이라도 있었는지 센다
    //   · 0이면 막지 않음 · 1 이상이면 막음 (SQL 은 AccessLogMapper.xml 의 countIpBlockTriggers)
    int countIpBlockTriggers(
            @Param("ipAddress") String ipAddress,
            @Param("failLimit") int failLimit,
            @Param("windowMinutes") int windowMinutes,
            @Param("blockHours") int blockHours);
}
