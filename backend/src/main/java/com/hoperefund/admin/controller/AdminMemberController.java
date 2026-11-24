package com.hoperefund.admin.controller;

import com.hoperefund.admin.dto.MaskedMemberResponseDto;
import com.hoperefund.admin.dto.MemberRoleUpdateDto;
import com.hoperefund.admin.service.AdminMemberService;
import com.hoperefund.common.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * F-20: 관리자 회원 목록 조회(마스킹) 및 회원 권한(USER <-> ADMIN) 변경 API 컨트롤러
 */
@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    /**
     * F-20: 마스킹 처리된 회원 목록 조회 API
     * URI: GET /api/admin/members
     */
    @GetMapping
    public ApiResponse<List<MaskedMemberResponseDto>> getMemberList(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        // 1-based 페이지 번호 및 최대 조회 건수(100건) 보정
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return adminMemberService.getMaskedMemberList(keyword, role, safePage, safeSize);
    }

    /**
     * F-20: 회원 권한 변경 API (USER <-> ADMIN)
     * URI: PUT /api/admin/members/{id}/role
     */
    @PutMapping("/{id}/role")
    public ApiResponse<Void> updateMemberRole(
            @PathVariable("id") Long targetMemberId,
            @Valid @RequestBody MemberRoleUpdateDto dto,
            @AuthenticationPrincipal Long currentAdminId) {
        adminMemberService.updateMemberRole(targetMemberId, dto.getRole(), currentAdminId);
        return ApiResponse.ok(null);
    }
}
