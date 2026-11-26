package com.hoperefund.admin.service;

import com.hoperefund.admin.dto.AdminInquiryResponseDTO;
import com.hoperefund.admin.dto.InquiryAnswerRequestDTO;
import com.hoperefund.admin.dto.InquiryCreateRequestDTO;
import java.util.List;

public interface AdminInquiryService {
    // F-17, F-18 고객센터 서비스 인터페이스

    // [추가 11/20] F-17: 문의 작성 · 이유: 문의 작성 기능이 없어서
    void createInquiry(Long memberId, InquiryCreateRequestDTO dto);

    // F-17: 내 문의 목록 (memberId = 로그인 토큰에서 꺼낸 회원번호)
    List<AdminInquiryResponseDTO> getInquiryList(Long memberId);

    // [수정 11/20] memberId 추가 · 이유(PR #44 리뷰): 본인 문의인지 확인해야 함 (남의 문의는 404)
    AdminInquiryResponseDTO getInquiryDetail(Long memberId, Long inquiryId);

    // [추가 11/20] F-18: 관리자용 전체 문의 목록 (status가 비면 전체)
    List<AdminInquiryResponseDTO> getAdminInquiryList(String status);

    // F-18
    // [수정 11/20] adminId 추가 · 이유(PR #44 리뷰): 답변자는 요청 본문이 아니라 로그인한 관리자 번호
    void updateInquiryAnswer(Long inquiryId, InquiryAnswerRequestDTO dto, Long adminId);
}
