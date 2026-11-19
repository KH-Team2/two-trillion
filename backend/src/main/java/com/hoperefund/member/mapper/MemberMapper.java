package com.hoperefund.member.mapper;

import com.hoperefund.member.dto.LoginMemberDTO;
import com.hoperefund.member.dto.MemberInsertDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MEMBER 테이블 조회 · 저장. SQL 은 resources/mapper/member/MemberMapper.xml 에 있다. */
@Mapper
public interface MemberMapper {

    // 같은 아이디의 회원 수 (탈퇴 회원 포함)
    int countByLoginId(String loginId);

    // 같은 이메일의 회원 수 (탈퇴 회원 포함)
    int countByEmail(String email);

    // 실행 후 member.memberId 에 새 번호가 채워진다 (XML 의 selectKey)
    int insertMember(MemberInsertDTO member);

    // 아이디로 회원 조회. 없으면 null
    LoginMemberDTO selectLoginMember(String loginId);

    // 로그인 실패: 실패 횟수 + 1. maxFailCount 번째면 lockMinutes 분 잠그고 횟수는 0
    // 값이 2개 이상이면 @Param 으로 XML 에서 쓸 이름을 붙인다
    int increaseLoginFail(
            @Param("memberId") long memberId,
            @Param("maxFailCount") int maxFailCount,
            @Param("lockMinutes") int lockMinutes);

    // 로그인 성공: 실패 횟수 · 잠금 초기화
    int resetLoginFail(long memberId);
}
