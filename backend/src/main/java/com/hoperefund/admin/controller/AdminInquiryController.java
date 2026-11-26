package com.hoperefund.admin.controller;

import com.hoperefund.admin.dto.AdminInquiryResponseDTO;
import com.hoperefund.admin.dto.InquiryAnswerRequestDTO;
import com.hoperefund.admin.dto.InquiryCreateRequestDTO;
import com.hoperefund.admin.service.AdminInquiryService;
import com.hoperefund.common.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 고객센터 문의 API (F-17 · F-07-01 / F-18 · F-07-02)
 *
 * <ul>
 *   <li>POST /api/inquiries : 문의 작성 (회원)
 *   <li>GET /api/inquiries : 내 문의 목록 (회원)
 *   <li>GET /api/inquiries/{id} : 내 문의 상세 · 남의 문의면 404 (회원)
 *   <li>GET /api/admin/inquiries?status= : 전체 문의 목록 (관리자)
 *   <li>PUT /api/admin/inquiries/{id}/answer : [확인 중] 처리 · 답변 등록 (관리자)
 * </ul>
 *
 * <p>/api/admin/** 주소는 SecurityConfig가 ROLE=ADMIN만 통과시킴 (일반 회원은 Controller까지 오지 못하고 403)
 *
 * <p>[수정 11/20] 반환값 ResponseEntity&lt;...&gt; → 팀 공통 봉투 ApiResponse&lt;...&gt; · 이유: 팀 API 규칙 (성공 · 실패 모두
 * {"success", "data", "meta", "error"} 한 모양 · backend/docs/api-rules.md)
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
            @Valid @RequestBody InquiryCreateRequestDTO dto) {
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
    public ApiResponse<List<AdminInquiryResponseDTO>> getInquiryList(
            @AuthenticationPrincipal Long memberId) {
        List<AdminInquiryResponseDTO> list = adminInquiryService.getInquiryList(memberId);
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
    public ApiResponse<AdminInquiryResponseDTO> getInquiryDetail(
            @AuthenticationPrincipal Long memberId, @PathVariable("id") Long inquiryId) {
        AdminInquiryResponseDTO detail = adminInquiryService.getInquiryDetail(memberId, inquiryId);
        return ApiResponse.ok(detail);
    }

    /**
     * [추가 11/20] F-18: 관리자용 전체 문의 목록 (최신순)
     * URI: GET /api/admin/inquiries?status=WAITING (status는 선택 · 비우면 전체)
     * 이유: 관리자가 답변할 문의를 고를 목록이 없었음 (기능명세서 F-07-02 ① 답변 대기 최신순 목록)
     */
    @GetMapping("/admin/inquiries")
    public ApiResponse<List<AdminInquiryResponseDTO>> getAdminInquiryList(
            @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.ok(adminInquiryService.getAdminInquiryList(status));
    }

    /**
     * F-18: 관리자 문의 답변 등록 및 상태 변경 API ([확인 중] 뱃지 및 답변 완료 처리)
     * URI: PUT /api/admin/inquiries/{id}/answer
     * - [확인 중] 버튼: {"status": "CHECKING"}
     * - 답변 등록: {"status": "ANSWERED", "answer": "답변 내용"}
     *
     * <p>[수정 11/20] ① @AuthenticationPrincipal Long adminId 추가 · 이유(PR #44 리뷰): 답변자는 로그인한 관리자(토큰)에서 받음
     * ② @Valid 추가 · 이유: DTO의 상태값 검사(@Pattern)를 실행하려면 필요
     */
    @PutMapping("/admin/inquiries/{id}/answer")
    public ApiResponse<Void> updateInquiryAnswer(
            @AuthenticationPrincipal Long adminId, // /api/admin/** 라서 이 번호는 항상 관리자 (SecurityConfig)
            @PathVariable("id") Long inquiryId,
            @Valid @RequestBody InquiryAnswerRequestDTO dto) {
        adminInquiryService.updateInquiryAnswer(inquiryId, dto, adminId);
        return ApiResponse.ok(null);
    }
}
