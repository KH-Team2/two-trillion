package com.hoperefund.admin.mapper;

import com.hoperefund.admin.dto.AdminInquiryResponseDTO;
import com.hoperefund.admin.dto.InquiryAnswerRequestDTO;
import com.hoperefund.admin.dto.InquiryCreateRequestDTO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** INQUIRY(문의) 조회 · 저장. SQL은 resources/mapper/admin/AdminInquiryMapper.xml 에 있다. */
@Mapper
public interface AdminInquiryMapper {

    // [추가 11/20] F-17: 문의 작성 · 이유: 문의 작성 API가 없어서
    //   memberId는 로그인 토큰에서 꺼낸 값 · dto는 유형 · 제목 · 내용
    int insertInquiry(@Param("memberId") Long memberId, @Param("dto") InquiryCreateRequestDTO dto);

    /* F-17, F-18: 고객센터 문의/답변 */
    // [수정 11/20] 주석 추가 · SQL에 WHERE MEMBER_ID = #{memberId} 조건을 넣음 (PR #44 리뷰: 모든 회원 문의가 조회됨)
    //   F-17: 로그인한 회원 본인의 문의 목록
    List<AdminInquiryResponseDTO> selectInquiryList(@Param("memberId") Long memberId);

    // [추가 11/20] F-18: 관리자용 전체 문의 목록 (상태로 거르기 · 비우면 전체)
    //   이유: 원래 XML의 status 조건은 관리자 목록용인데, 회원 목록과 한 쿼리에 섞여 있었음 → 둘로 나눔
    List<AdminInquiryResponseDTO> selectAdminInquiryList(@Param("status") String status);

    /* F-17: 문의 상세 조회 쿼리 매핑 */
    // [수정 11/20] memberId 파라미터 추가 · 이유(PR #44 리뷰): 문의 번호만 맞으면 남의 문의도 보였음
    //   → WHERE INQUIRY_ID = #{inquiryId} AND MEMBER_ID = #{memberId} (남의 것이면 결과 없음 → 404)
    AdminInquiryResponseDTO selectInquiryById(
            @Param("inquiryId") Long inquiryId, @Param("memberId") Long memberId);

    // [추가 11/20] F-18: 문의의 지금 상태만 조회 (없는 번호면 null)
    //   이유: 답변 등록 전에 「없는 문의(404)」 · 「이미 답변 완료(409)」를 구분해서 알려 주려고
    String selectInquiryStatus(@Param("inquiryId") Long inquiryId);

    /* F-18: 문의 답변 등록 및 상태 변경 쿼리 매핑 */
    // [수정 11/20] adminId 파라미터 추가 · 이유(PR #44 리뷰): ANSWERED면 ANSWERED_BY(답변자)가 꼭 있어야 함
    // (CK_INQUIRY_ANSWER)
    //   답변자는 요청 본문이 아니라 로그인 토큰에서 꺼낸 관리자 번호
    int updateInquiryAnswer(
            @Param("inquiryId") Long inquiryId,
            @Param("dto") InquiryAnswerRequestDTO dto,
            @Param("adminId") Long adminId);
}
