package com.hoperefund.member.mapper;

import com.hoperefund.member.dto.LoginMemberDTO;
import com.hoperefund.member.dto.MemberInsertDTO;
import com.hoperefund.member.dto.MemberProfileDTO;
import com.hoperefund.member.dto.MemberProfileRequestDTO;
import com.hoperefund.member.dto.PasswordResetMemberDTO;
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

    // 아이디 찾기 · 비밀번호 재설정 (F-01-03): 이메일로 회원 조회. 없으면 null
    PasswordResetMemberDTO selectResetMember(String email);

    // 비밀번호 재설정 (F-01-03): 새 BCrypt 암호문 저장 · 수정일 갱신
    int updatePassword(@Param("memberId") long memberId, @Param("password") String password);

    // 회원정보 조회 (F-01-04): 로그인한 본인 · 탈퇴하지 않은 회원만. 없으면 null
    MemberProfileDTO selectProfile(long memberId);

    // 회원정보 · 자격 정보 수정 (F-01-04): ROLE · STATUS 는 바꾸지 않는다. 고친 행 수 (0 = 없거나 탈퇴)
    int updateProfile(
            @Param("memberId") long memberId, @Param("profile") MemberProfileRequestDTO profile);

    // 비밀번호 변경 (F-01-04): 현재 BCrypt 암호문. 없거나 탈퇴 회원이면 null
    String selectPassword(long memberId);

    // 회원 탈퇴 (F-01-05): STATUS · WITHDRAWN_AT · ROLE 변경 (아이디 · 이메일 유지). 고친 행 수 (0 = 없거나 이미 탈퇴)
    int withdrawMember(long memberId);
}
