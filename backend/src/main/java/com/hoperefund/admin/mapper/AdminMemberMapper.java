package com.hoperefund.admin.mapper;

import com.hoperefund.admin.dto.MaskedMemberResponseDTO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * F-20: 회원 목록 조회 및 권한 관리 MyBatis 매퍼 인터페이스
 */
@Mapper
public interface AdminMemberMapper {

    // 검색 조건(키워드, 권한) 및 페이징으로 마스킹된 회원 목록 조회
    List<MaskedMemberResponseDTO> selectMaskedMemberList(
            @Param("keyword") String keyword,
            @Param("role") String role,
            @Param("offset") int offset,
            @Param("size") int size);

    // 검색 조건에 일치하는 전체 회원 수 조회 (PageMeta total 산출용)
    long countMaskedMembers(@Param("keyword") String keyword, @Param("role") String role);

    // 회원 단건 조회 (권한 변경 전 존재 여부 및 성별/현재 권한 검증용)
    MaskedMemberResponseDTO selectMemberById(@Param("memberId") Long memberId);

    // 회원 권한(ROLE) 및 수정일시(UPDATED_AT) 업데이트
    int updateMemberRole(@Param("memberId") Long memberId, @Param("role") String role);
}
