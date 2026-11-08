package com.hoperefund.member.mapper;

import com.hoperefund.member.dto.MemberInsertDTO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 회원(MEMBER) DB 조회·저장 Mapper
 * SQL은 resources/mapper/member/MemberMapper.xml에 작성한다.
 */
@Mapper
public interface MemberMapper {

    // 같은 아이디의 회원 수 (탈퇴 회원 포함)
    int countByLoginId(String loginId);

    // 같은 이메일의 회원 수 (탈퇴 회원 포함)
    int countByEmail(String email);

    // 회원 저장 (memberId는 selectKey로 채워짐)
    int insertMember(MemberInsertDTO member);
}
