package com.hoperefund.admin.service;

import com.hoperefund.admin.dto.AdminInquiryResponseDto;
import com.hoperefund.admin.dto.InquiryAnswerRequestDto;
import java.util.List;

public interface AdminInquiryService {
    // F-17, F-18 고객센터 서비스 인터페이스
    List<AdminInquiryResponseDto> getInquiryList(Long memberId);
    AdminInquiryResponseDto getInquiryDetail(Long inquiryId);
    
    // F-18
    void updateInquiryAnswer(Long inquiryId, InquiryAnswerRequestDto dto);
}