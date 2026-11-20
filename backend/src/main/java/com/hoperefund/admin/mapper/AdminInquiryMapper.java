package com.hoperefund.admin.mapper;

import com.hoperefund.admin.dto.AdminInquiryResponseDto;
import com.hoperefund.admin.dto.InquiryCreateRequestDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** INQUIRY(문의) 조회 · 저장. SQL은 resources/mapper/admin/AdminInquiryMapper.xml 에 있다. */
@Mapper
public interface AdminInquiryMapper {

    // [추가 11/20] F-17: 문의 작성 · 이유: 문의 작성 API가 없어서
    //   memberId는 로그인 토큰에서 꺼낸 값 · dto는 유형 · 제목 · 내용
    int insertInquiry(@Param("memberId") Long memberId, @Param("dto") InquiryCreateRequestDto dto);

    /* F-17, F-18: 고객센터 문의/답변 */
    // [수정 11/20] 주석 추가 · SQL에 WHERE MEMBER_ID = #{memberId} 조건을 넣음 (PR #44 리뷰: 모든 회원 문의가 조회됨)
    //   F-17: 로그인한 회원 본인의 문의 목록
    List<AdminInquiryResponseDto> selectInquiryList(@Param("memberId") Long memberId);

    /* F-17: 문의 상세 조회 쿼리 매핑 */
    // [수정 11/20] memberId 파라미터 추가 · 이유(PR #44 리뷰): 문의 번호만 맞으면 남의 문의도 보였음
    //   → WHERE INQUIRY_ID = #{inquiryId} AND MEMBER_ID = #{memberId} (남의 것이면 결과 없음 → 404)
    AdminInquiryResponseDto selectInquiryById(
            @Param("inquiryId") Long inquiryId, @Param("memberId") Long memberId);
}
