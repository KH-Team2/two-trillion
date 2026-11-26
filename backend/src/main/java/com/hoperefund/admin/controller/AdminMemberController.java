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

}
