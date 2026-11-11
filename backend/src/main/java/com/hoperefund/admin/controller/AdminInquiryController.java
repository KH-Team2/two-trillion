package com.hoperefund.admin.controller;

import com.hoperefund.admin.dto.AdminInquiryResponseDto;
import com.hoperefund.admin.dto.InquiryAnswerRequestDto;
import com.hoperefund.admin.service.AdminInquiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AdminInquiryController {
	/* F-17, F-18 */
    private final AdminInquiryService adminInquiryService;

    /**
     * F-17: 내 문의 목록 조회 API
     * URI: GET /api/inquiries
     */
    @GetMapping("/inquiries")
    public ResponseEntity<List<AdminInquiryResponseDto>> getInquiryList(
            @RequestParam(value = "memberId", required = false) Long memberId) {
        List<AdminInquiryResponseDto> list = adminInquiryService.getInquiryList(memberId);
        return ResponseEntity.ok(list);
    }

    /**
     * F-17: 문의 상세 조회 API
     * URI: GET /api/inquiries/{id}
     */
    @GetMapping("/inquiries/{id}")
    public ResponseEntity<AdminInquiryResponseDto> getInquiryDetail(@PathVariable("id") Long inquiryId) {
        AdminInquiryResponseDto detail = adminInquiryService.getInquiryDetail(inquiryId);
        return ResponseEntity.ok(detail);
    }

    /**
     * F-18: 관리자 문의 답변 등록 및 상태 변경 API ([확인 중] 뱃지 및 답변 완료 처리)
     * URI: PUT /api/admin/inquiries/{id}/answer
     */
    @PutMapping("/admin/inquiries/{id}/answer")
    public ResponseEntity<Void> updateInquiryAnswer(
            @PathVariable("id") Long inquiryId,
            @RequestBody InquiryAnswerRequestDto dto) {
        adminInquiryService.updateInquiryAnswer(inquiryId, dto);
        return ResponseEntity.ok().build();
    }
}