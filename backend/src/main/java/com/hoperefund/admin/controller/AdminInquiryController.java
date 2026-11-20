package com.hoperefund.admin.controller;

import com.hoperefund.admin.dto.AdminInquiryResponseDto;
import com.hoperefund.admin.dto.InquiryCreateRequestDto;
import com.hoperefund.admin.service.AdminInquiryService;
import com.hoperefund.common.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 고객센터 문의 API (F-17 · F-07-01) · 관리자 답변(F-18 · F-07-02)은 F-18 PR에서 추가
 *
 * <ul>
 *   <li>POST /api/inquiries : 문의 작성 (회원)
 *   <li>GET /api/inquiries : 내 문의 목록 (회원)
 *   <li>GET /api/inquiries/{id} : 내 문의 상세 · 남의 문의면 404 (회원)
 * </ul>
 *
 * <p>[수정 11/20] F-17 API 반환값 ResponseEntity&lt;...&gt; → 팀 공통 봉투 ApiResponse&lt;...&gt; · 이유: 팀 API 규칙
 * (성공 · 실패 모두 {"success", "data", "meta", "error"} 한 모양 · backend/docs/api-rules.md)
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AdminInquiryController {
    /* F-17, F-18: 고객센터 문의 확인 및 답변 */
    private final AdminInquiryService adminInquiryService;

    /**
     * [추가 11/20] F-17: 문의 작성 API · 이유: 문의 작성 API가 없어서
     * URI: POST /api/inquiries
     * 성공하면 data는 null (팀 규칙: 돌려줄 데이터가 없으면 null · 작성 후 화면이 목록을 다시 불러옴)
     */
    @PostMapping("/inquiries")
    public ApiResponse<Void> createInquiry(
            @AuthenticationPrincipal Long memberId, // 로그인 토큰에서 꺼낸 회원번호
            // @Valid: InquiryCreateRequestDto의 검사(@NotBlank · @Pattern · @Size)를 실행 · 어기면 여기 들어오기
            // 전에 400
            @Valid @RequestBody InquiryCreateRequestDto dto) {
        adminInquiryService.createInquiry(memberId, dto);
        return ApiResponse.ok(null);
    }

    /**
     * F-17: 내 문의 목록 조회 API
     * URI: GET /api/inquiries
     *
     * <p>[수정 11/20] @RequestParam("memberId") → @AuthenticationPrincipal
     * 이유(PR #44 리뷰): ?memberId= 숫자만 바꾸면 다른 회원 문의를 볼 수 있었음 → 로그인 토큰에서 회원번호를 꺼냄 (로그인 #37 이후 다른 API와 같은 방식)
     */
    @GetMapping("/inquiries")
    public ApiResponse<List<AdminInquiryResponseDto>> getInquiryList(
            @AuthenticationPrincipal Long memberId) {
        List<AdminInquiryResponseDto> list = adminInquiryService.getInquiryList(memberId);
        return ApiResponse.ok(list);
    }

    /**
     * F-17: 문의 상세 조회 API
     * URI: GET /api/inquiries/{id}
     *
     * <p>[수정 11/20] @AuthenticationPrincipal Long memberId 추가 · 이유(PR #44 리뷰): 문의 번호만 맞으면 남의 문의도 보였음
     * → Service가 본인 문의인지 확인하고, 아니면 404로 숨김
     */
    @GetMapping("/inquiries/{id}")
    public ApiResponse<AdminInquiryResponseDto> getInquiryDetail(
            @AuthenticationPrincipal Long memberId, @PathVariable("id") Long inquiryId) {
        AdminInquiryResponseDto detail = adminInquiryService.getInquiryDetail(memberId, inquiryId);
        return ApiResponse.ok(detail);
    }
}
