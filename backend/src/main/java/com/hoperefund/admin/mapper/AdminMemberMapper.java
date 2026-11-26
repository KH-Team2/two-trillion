package com.hoperefund.admin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * F-20: 회원 목록 조회 및 권한 관리 MyBatis 매퍼 인터페이스
 */
@Mapper
public interface AdminMemberMapper {


    // 회원 권한(ROLE) 및 수정일시(UPDATED_AT) 업데이트
    int updateMemberRole(@Param("memberId") Long memberId, @Param("role") String role);
}
