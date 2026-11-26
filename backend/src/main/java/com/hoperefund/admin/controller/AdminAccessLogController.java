package com.hoperefund.admin.controller;

<<<<<<< HEAD
import com.hoperefund.admin.dto.AccessLogResponseDTO;
import com.hoperefund.admin.service.AdminAccessLogService;
import com.hoperefund.common.dto.ApiResponse;
=======
import com.hoperefund.admin.dto.AccessLogResponseDto;
import com.hoperefund.admin.service.AdminAccessLogService;
import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.common.dto.PageMeta;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
>>>>>>> aad4652af0ed7a61c0d2e67402c6a605f34e7053
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
<<<<<<< HEAD
import org.springframework.web.bind.annotation.*;

/**
 * F-21: 관리자 접근 기록 조회 API 컨트롤러
 */
=======
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

>>>>>>> aad4652af0ed7a61c0d2e67402c6a605f34e7053
@RestController
@RequestMapping("/api/admin/access-logs")
@RequiredArgsConstructor
public class AdminAccessLogController {
<<<<<<< HEAD

    // 한 페이지당 조회 가능한 최대 건수 제한
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminAccessLogService adminAccessLogService;

    /**
     * 접근 기록 목록 조회 (GET /api/admin/access-logs)
     * - startDate, endDate: ISO 날짜(YYYY-MM-DD) 형식으로 바인딩 (형식 오류 시 400 처리)
     * - page: 1부터 시작하는 페이지 번호 (최소 1 보정)
     * - size: 페이지 크기 (1 ~ 100 범위로 제한)
     */
    @GetMapping
    public ApiResponse<List<AccessLogResponseDTO>> getAccessLogs(
=======
    /* F-21 (접근 기록 조회 및 실패 횟수 모니터링) */
    private final AdminAccessLogService adminAccessLogService;

    // [추가 11/20] 한 페이지 최대 건수 · 이유(PR #45 리뷰 ①): 최대값이 없어 size=100000이면 기록을 통째로 가져옴
    private static final int MAX_SIZE = 100;

    /**
     *
     * F-21: 접근 기록 및 로그인 실패 모니터링 조회 (GET /api/admin/access-logs)
     * - 쿼리 파라미터(선택): startDate, endDate, ipAddress, page, size
     * - 예: /api/admin/access-logs?startDate=2026-11-01&endDate=2026-11-20&page=1&size=10
     * - /api/admin/** 라서 관리자(ROLE=ADMIN) 토큰만 들어와요 (아니면 SecurityConfig가 403)
     *
     * <p>[수정 11/20] PR #45 리뷰 반영
     * ① 날짜: String → @DateTimeFormat(iso = DATE) LocalDate · 2026-11-32처럼 틀리면 Service 전에 400 (원래는 DB 오류 500)
     * ② page: 기본값 0 → 1 (팀 공통 PageMeta는 1부터) · @Min(1)
     * ③ size: @Min(1) @Max(100) · 범위를 벗어나면 400
     * ④ 반환값: ResponseEntity&lt;List&gt; → ApiResponse.ok(목록, PageMeta.of(page, size, total)) · 전체 개수를 같이 보냄
     *
     * <p>[추가 11/23] 조회할 때마다 「누가 · 어느 IP에서 봤는지」를 ACCESS_LOG에 남겨요 (기능명세서 F-08-02 비고)
     */
    @GetMapping
    public ApiResponse<List<AccessLogResponseDto>> getAccessLogs(
            // [추가 11/23] 로그인 토큰에서 꺼낸 관리자 회원 번호 (기록의 MEMBER_ID로 저장)
            @AuthenticationPrincipal Long adminId,
            // [추가 11/23] HttpServletRequest: 지금 들어온 요청 자체 · getRemoteAddr()로 접속 IP를 꺼내요
            //   X-Forwarded-For 헤더는 화면이 마음대로 바꿀 수 있어 쓰지 않아요 (로그인 기록과 같은 방식 · MemberController)
            HttpServletRequest request,
            // @DateTimeFormat(iso = ISO.DATE): "2026-11-20" 모양의 글자를 LocalDate(날짜)로 바꿔 줌 · 바꿀 수 없으면
            // 400
>>>>>>> aad4652af0ed7a61c0d2e67402c6a605f34e7053
            @RequestParam(value = "startDate", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate,
            @RequestParam(value = "ipAddress", required = false) String ipAddress,
<<<<<<< HEAD
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        return adminAccessLogService.getAccessLogs(
                startDate, endDate, ipAddress, safePage, safeSize);
=======
            // @Min · @Max: 숫자 범위 검사 · 벗어나면 Controller 실행 전에 400 (공통 처리기: 「요청 값을 확인해 주세요」)
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "10") @Min(1) @Max(MAX_SIZE) int size) {
        List<AccessLogResponseDto> logs =
                adminAccessLogService.getAccessLogs(startDate, endDate, ipAddress, page, size);
        int total = adminAccessLogService.countAccessLogs(startDate, endDate, ipAddress);
        // [추가 11/23] 조회가 끝난 뒤에 기록 → 이번 조회 기록은 다음 조회부터 목록에 보여요
        adminAccessLogService.recordView(adminId, request.getRemoteAddr());
        return ApiResponse.ok(logs, PageMeta.of(page, size, total));
>>>>>>> aad4652af0ed7a61c0d2e67402c6a605f34e7053
    }
}
