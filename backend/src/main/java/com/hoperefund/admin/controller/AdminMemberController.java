package com.hoperefund.admin.controller;

import com.hoperefund.admin.dto.MaskedMemberResponseDto;
import com.hoperefund.admin.dto.MemberRoleUpdateDto;
import com.hoperefund.admin.service.AdminMemberService;
import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.common.dto.PageMeta;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {
    /*F-20: 회원 목록 조회 및 권한 변경*/
    private final AdminMemberService adminMemberService;

    // [추가 11/23 고은] 한 쪽 최대 건수 · 이유: F-21 리뷰(PR #45 ①)와 같이 size=100000으로 통째로 가져가는 것 막기
    private static final int MAX_SIZE = 100;

    /**
     * F-20: 마스킹 회원 목록 조회 (GET /api/admin/members)
     * - 예: /api/admin/members?keyword=naver&role=ADMIN&status=ACTIVE&page=1&size=20
     * - /api/admin/** 라서 관리자 토큰만 들어와요 (아니면 SecurityConfig가 403)
     *
     * <p>[수정 11/23 고은]
     * ① 반환값: ResponseEntity&lt;List&gt; → ApiResponse.ok(목록, PageMeta) · 팀 공통 응답 모양 + 전체 개수
     * ② page 기본값 0 → 1 (팀 공통 PageMeta는 1부터) · @Min(1) · size는 @Min(1) @Max(100)
     * ③ role · status는 정해진 값만 (@Pattern) · 틀리면 400
     * ④ 조회할 때마다 「누가 · 어느 IP에서 봤는지」 ACCESS_LOG에 기록 (명세 F-08-02 비고)
     */
    @GetMapping
    public ApiResponse<List<MaskedMemberResponseDto>> getMemberList(
            // [추가 11/23 고은] 로그인 토큰에서 꺼낸 관리자 회원 번호 (기록의 MEMBER_ID)
            @AuthenticationPrincipal Long adminId,
            // [추가 11/23 고은] 접속 IP를 꺼내려고 (getRemoteAddr · X-Forwarded-For는 바꿀 수 있어 안 씀)
            HttpServletRequest request,
            @RequestParam(value = "keyword", required = false) String keyword,
            // [수정 11/23 고은] @Pattern 추가 · 빈 글자("")도 허용 = 조건 없음
            @RequestParam(value = "role", required = false) @Pattern(regexp = "|USER|ADMIN")
                    String role,
            // [추가 11/23 고은] 계정 상태 ACTIVE / WITHDRAWN
            @RequestParam(value = "status", required = false) @Pattern(regexp = "|ACTIVE|WITHDRAWN")
                    String status,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "10") @Min(1) @Max(MAX_SIZE) int size) {
        List<MaskedMemberResponseDto> members =
                adminMemberService.getMaskedMemberList(keyword, role, status, page, size);
        int total = adminMemberService.countMembers(keyword, role, status);
        adminMemberService.recordView(adminId, request.getRemoteAddr());
        return ApiResponse.ok(members, PageMeta.of(page, size, total));
    }

    /**
     * F-20: 회원 권한 변경 (USER ↔ ADMIN) (PUT /api/admin/members/{id}/role · 본문 {"role":"ADMIN"})
     * - 예외 처리: 자기 자신 권한 해제 불가, 성별 미입력 관리자의 일반 회원 변경 불가
     *
     * <p>[수정 11/23 고은]
     * ① @RequestAttribute("currentAdminId") → @AuthenticationPrincipal
     *    이유: 우리 서버에는 currentAdminId를 넣어 주는 곳이 없어서 매번 400이었어요 · 문의 답변(F-18)과 같은 방식
     * ② try-catch + ResponseEntity&lt;String&gt; 삭제 → 공통 예외(404 · 409)를 GlobalExceptionHandler가 처리
     *    이유: 실패해도 팀 공통 실패 모양 { success:false, error:{code, message} }으로 나가게
     * ③ @Valid 추가 · role이 USER/ADMIN이 아니면 400
     * ④ 성공 응답: ApiResponse.ok(null) → { success:true, data:null }
     */
    @PutMapping("/{id}/role")
    public ApiResponse<Void> updateMemberRole(
            @PathVariable("id") Long memberId,
            @Valid @RequestBody MemberRoleUpdateDto requestDto,
            @AuthenticationPrincipal Long currentAdminId, // 현재 로그인한 관리자 ID (토큰에서)
            HttpServletRequest request) {
        adminMemberService.updateMemberRole(
                memberId, requestDto.getRole(), currentAdminId, request.getRemoteAddr());
        return ApiResponse.ok(null);
    }
}
