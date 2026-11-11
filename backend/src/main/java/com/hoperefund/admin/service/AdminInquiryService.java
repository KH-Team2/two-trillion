package com.hoperefund.admin.service;

import com.hoperefund.admin.dto.AdminInquiryResponseDto;
import com.hoperefund.admin.dto.InquiryAnswerRequestDto;
import java.util.List;

public interface AdminInquiryService {
    // F-17
    List<AdminInquiryResponseDto> getInquiryList(Long memberId);
    AdminInquiryResponseDto getInquiryDetail(Long inquiryId);
    
    // F-18
    void updateInquiryAnswer(Long inquiryId, InquiryAnswerRequestDto dto);
}