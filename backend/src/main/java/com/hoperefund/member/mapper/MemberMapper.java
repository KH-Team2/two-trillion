package com.hoperefund.member.mapper;

import com.hoperefund.member.dto.LoginMemberDTO;
import com.hoperefund.member.dto.MemberInsertDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    // 로그인: 아이디로 회원 조회 (없으면 null · 잠금 여부는 DB 시각으로 계산)
    LoginMemberDTO selectLoginMember(String loginId);

    // 로그인 실패: 실패 횟수 + 1 · maxFailCount번째면 lockMinutes분 잠금 후 횟수 0
    int increaseLoginFail(
            @Param("memberId") long memberId,
            @Param("maxFailCount") int maxFailCount,
            @Param("lockMinutes") int lockMinutes);

    // 로그인 성공: 실패 횟수 · 잠금 초기화
    int resetLoginFail(long memberId);
}
