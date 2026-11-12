package com.hoperefund.admin.service;

import com.hoperefund.admin.dto.AdminInquiryResponseDto;
import com.hoperefund.admin.dto.InquiryAnswerRequestDto;
import com.hoperefund.admin.mapper.AdminInquiryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminInquiryServiceImpl implements AdminInquiryService {
	/* F-17, F-18 비즈니스 로직 구현체 */
    private final AdminInquiryMapper adminInquiryMapper;

    @Override
    public List<AdminInquiryResponseDto> getInquiryList(Long memberId) {
        return adminInquiryMapper.selectInquiryList(memberId);
    }

    @Override
    public AdminInquiryResponseDto getInquiryDetail(Long inquiryId) {
        AdminInquiryResponseDto detail = adminInquiryMapper.selectInquiryById(inquiryId);
        if (detail == null) {
            throw new IllegalArgumentException("존재하지 않는 문의 내역입니다.");
        }
        return detail;
    }

    @Override
    @Transactional
    public void updateInquiryAnswer(Long inquiryId, InquiryAnswerRequestDto dto) {
        if (dto.getStatus() == null || dto.getStatus().trim().isEmpty()) {
            throw new IllegalArgumentException("변경할 상태값을 입력해주세요.");
        }
        
        int result = adminInquiryMapper.updateInquiryAnswer(inquiryId, dto);
        if (result == 0) {
            throw new IllegalArgumentException("답변 등록에 실패했습니다. 존재하지 않는 문의 ID입니다.");
        }
    }
}