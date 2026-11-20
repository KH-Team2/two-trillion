package com.hoperefund.admin.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoperefund.admin.dto.AdminInquiryResponseDto;
import com.hoperefund.admin.dto.InquiryCreateRequestDto;
import com.hoperefund.admin.mapper.AdminInquiryMapper;
import com.hoperefund.common.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * F-17, F-18 비즈니스 로직 구현체
 *
 * <p>[수정 11/20] F-17 예외를 IllegalArgumentException → 팀 공통 예외(NotFoundException)로 바꿈 · 이유: 공통
 * GlobalExceptionHandler는 IllegalArgumentException을 따로 처리하지 않아서 「모든 오류 → 500 서버 오류」로 나갔음.
 * 공통 예외를 던지면 404와 화면 문구가 알맞게 나감 (backend/AGENTS.md API 규칙). F-18(관리자 답변)은 F-18 PR에서 추가.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminInquiryServiceImpl implements AdminInquiryService {
    /* F-17, F-18 비즈니스 로직 구현체 */
    private final AdminInquiryMapper adminInquiryMapper;

    // [추가 11/20] F-17: 문의 작성
    //   유형 · 제목 · 내용 검사는 DTO의 @Valid가 이미 끝냄 → 여기서는 저장만
    @Override
    @Transactional // 저장은 읽기 전용(readOnly)이 아니라서 따로 붙임
    public void createInquiry(Long memberId, InquiryCreateRequestDto dto) {
        adminInquiryMapper.insertInquiry(memberId, dto);
    }

    @Override
    public List<AdminInquiryResponseDto> getInquiryList(Long memberId) {
        return adminInquiryMapper.selectInquiryList(memberId);
    }

    // [수정 11/20] memberId 추가 + 예외 종류 변경
    //   이유(PR #44 리뷰): 본인 문의만 보이게 · 남의 문의는 「없는 문의」와 같은 404로 숨김
    //   (403으로 보내면 「그 번호의 문의가 있긴 하다」는 걸 알려 주게 됨 · 팀 규칙 F-09-02)
    @Override
    public AdminInquiryResponseDto getInquiryDetail(Long memberId, Long inquiryId) {
        AdminInquiryResponseDto detail = adminInquiryMapper.selectInquiryById(inquiryId, memberId);
        if (detail == null) {
            // [수정 11/20] IllegalArgumentException("존재하지 않는 문의 내역입니다.") → NotFoundException (500 → 404)
            throw new NotFoundException("문의를 찾을 수 없어요");
        }
        return detail;
    }
}
