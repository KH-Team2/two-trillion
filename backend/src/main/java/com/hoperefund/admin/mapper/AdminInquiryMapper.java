package com.hoperefund.admin.mapper;

import com.hoperefund.admin.dto.AdminInquiryResponseDto;
import com.hoperefund.admin.dto.InquiryAnswerRequestDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface AdminInquiryMapper {
	/* F-17, F-18: 고객센터 문의/답변 */
    List<AdminInquiryResponseDto> selectInquiryList(@Param("memberId") Long memberId);

    /* F-17: 문의 상세 조회 쿼리 매핑 */
    AdminInquiryResponseDto selectInquiryById(@Param("inquiryId") Long inquiryId);

    /* F-18: 문의 답변 등록 및 상태 변경 쿼리 매핑 */
    int updateInquiryAnswer(
            @Param("inquiryId") Long inquiryId, 
            @Param("dto") InquiryAnswerRequestDto dto
    );
}