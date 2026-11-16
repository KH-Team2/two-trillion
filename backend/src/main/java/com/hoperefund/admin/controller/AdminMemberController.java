package com.hoperefund.admin.controller;

import com.hoperefund.admin.dto.MaskedMemberResponseDto;
import com.hoperefund.admin.dto.MemberRoleUpdateDto;
import com.hoperefund.admin.service.AdminMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {
	/*F-20: 회원 목록 조회 및 권한 변경*/
    private final AdminMemberService adminMemberService;

    /**
     * F-20: 마스킹 회원 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<MaskedMemberResponseDto>> getMemberList(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        List<MaskedMemberResponseDto> members = adminMemberService.getMaskedMemberList(keyword, role, page, size);
        return ResponseEntity.ok(members);
    }

    /**
     * 
     * F-20: 회원 권한 변경 (USER ↔ ADMIN)
     * - 예외 처리: 자기 자신 권한 해제 불가, 성별 미입력 관리자의 일반 회원 변경 불가
     */
    @PutMapping("/{id}/role")
    public ResponseEntity<String> updateMemberRole(
            @PathVariable("id") Long memberId,
            @RequestBody MemberRoleUpdateDto requestDto,
            @RequestAttribute("currentAdminId") Long currentAdminId // 현재 로그인한 관리자 ID
    ) {
        try {
            adminMemberService.updateMemberRole(memberId, requestDto.getRole(), currentAdminId);
            return ResponseEntity.ok("회원 권한이 성공적으로 변경되었습니다.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}