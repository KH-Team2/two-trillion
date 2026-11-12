package com.hoperefund.admin.mapper;

import com.hoperefund.admin.dto.MaskedMemberResponseDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminMemberMapper {
	/* F-20: 회원 및 권한 관리 */
    List<MaskedMemberResponseDto> selectMaskedMemberList(
        @Param("keyword") String keyword, 
        @Param("role") String role, 
        @Param("offset") int offset, 
        @Param("size") int size
    );

    MaskedMemberResponseDto selectMemberById(@Param("memberId") Long memberId);

    int updateMemberRole(@Param("memberId") Long memberId, @Param("role") String role);
}