package com.hoperefund.admin.service;

import com.hoperefund.admin.dto.AdminInquiryResponseDto;
import com.hoperefund.admin.dto.InquiryCreateRequestDto;
import java.util.List;

public interface AdminInquiryService {
    // F-17, F-18 고객센터 서비스 인터페이스

    // [추가 11/20] F-17: 문의 작성 · 이유: 문의 작성 기능이 없어서
    void createInquiry(Long memberId, InquiryCreateRequestDto dto);

    // F-17: 내 문의 목록 (memberId = 로그인 토큰에서 꺼낸 회원번호)
    List<AdminInquiryResponseDto> getInquiryList(Long memberId);

    // [수정 11/20] memberId 추가 · 이유(PR #44 리뷰): 본인 문의인지 확인해야 함 (남의 문의는 404)
    AdminInquiryResponseDto getInquiryDetail(Long memberId, Long inquiryId);
}
