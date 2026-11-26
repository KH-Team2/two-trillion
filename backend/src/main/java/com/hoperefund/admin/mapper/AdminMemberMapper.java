package com.hoperefund.admin.mapper;

<<<<<<< HEAD
=======
import com.hoperefund.admin.dto.AdminMemberRoleTargetDto;
import com.hoperefund.admin.dto.MaskedMemberResponseDto;
>>>>>>> db68705e28145ea518a7b85159408da8ab05e40f
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

<<<<<<< HEAD
/**
 * F-20: 회원 목록 조회 및 권한 관리 MyBatis 매퍼 인터페이스
 */
@Mapper
public interface AdminMemberMapper {


    // 회원 권한(ROLE) 및 수정일시(UPDATED_AT) 업데이트
=======
@Mapper
public interface AdminMemberMapper {
    /* F-20: 회원 및 권한 관리 */

    // [수정 11/23 고은] status(계정 상태) 검색 조건 추가
    List<MaskedMemberResponseDto> selectMaskedMemberList(
            @Param("keyword") String keyword,
            @Param("role") String role,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("size") int size);

    // [추가 11/23 고은] 검색 조건에 맞는 전체 회원 수 · 이유: 화면이 총 쪽 수를 그리려면 필요 (F-21과 같은 방식)
    int countMembers(
            @Param("keyword") String keyword,
            @Param("role") String role,
            @Param("status") String status);

    // [수정 11/23 고은] selectMemberById → selectRoleTarget · 반환 상자도 AdminMemberRoleTargetDto로
    //   이유: 목록 상자에서 성별을 빼서, 검사용 칸(권한 · 상태 · 성별)만 읽는 쿼리로 나눔
    AdminMemberRoleTargetDto selectRoleTarget(@Param("memberId") Long memberId);

>>>>>>> db68705e28145ea518a7b85159408da8ab05e40f
    int updateMemberRole(@Param("memberId") Long memberId, @Param("role") String role);
}
