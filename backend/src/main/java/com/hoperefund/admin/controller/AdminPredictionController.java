package com.hoperefund.admin.controller;

import com.hoperefund.admin.dto.AdminPredictionResponseDto;
import com.hoperefund.admin.service.AdminPredictionService;
import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.common.dto.PageMeta;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 예측 결과 조회 API (F-08-02 ② · S-08) · [신규 11/26 고은]
 *
 * <p>- /api/admin/** 라서 관리자(ROLE=ADMIN) 토큰만 들어와요 (일반 회원 403 · 토큰 없음 401 · SecurityConfig)
 *
 * <p>- 보기만 하는 기능이라 수정 · 삭제 API는 없어요 (명세 ② 「예측 결과 목록 조회·검색」)
 */
@RestController
@RequestMapping("/api/admin/predictions")
@RequiredArgsConstructor
public class AdminPredictionController {

    private final AdminPredictionService adminPredictionService;

    // 한 쪽 최대 건수 · 이유: size=100000처럼 통째로 가져가는 것 막기 (F-20 · F-21과 같음)
    private static final int MAX_SIZE = 100;

    /**
     * 예측 결과 목록 (GET /api/admin/predictions)
     *
     * <p>예) /api/admin/predictions?keyword=naver&amp;taxYear=2026&amp;kind=SCENARIO&amp;page=1&amp;size=20
     *
     * <p>- @RequestParam(required = false): 안 보내도 되는 값 (비면 그 조건 없이 전체)
     *
     * <p>- @Min · @Max · @Pattern: 범위 · 모양 검사 · 틀리면 이 메서드 실행 전에 400 「요청 값을 확인해 주세요」
     *
     * <p>- 응답: { success, data: 목록, meta: { page, size, total, totalPages } }
     */
    @GetMapping
    public ApiResponse<List<AdminPredictionResponseDto>> getPredictions(
            // 로그인 토큰에서 꺼낸 관리자 회원 번호 (기록의 MEMBER_ID)
            @AuthenticationPrincipal Long adminId,
            // 접속 IP를 꺼내려고 (getRemoteAddr · X-Forwarded-For는 사용자가 바꿀 수 있어 안 씀)
            HttpServletRequest request,
            // 이메일 일부
            @RequestParam(value = "keyword", required = false) String keyword,
            // 귀속 연도 · Integer(상자 숫자)라서 안 보내면 null = 전체
            @RequestParam(value = "taxYear", required = false) @Min(2000) @Max(2100)
                    Integer taxYear,
            // CURRENT(처음 계산) / SCENARIO(조건 변경) · 빈 글자("")도 허용 = 전체
            @RequestParam(value = "kind", required = false) @Pattern(regexp = "|CURRENT|SCENARIO")
                    String kind,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "10") @Min(1) @Max(MAX_SIZE) int size) {
        List<AdminPredictionResponseDto> rows =
                adminPredictionService.getPredictions(keyword, taxYear, kind, page, size);
        int total = adminPredictionService.countPredictions(keyword, taxYear, kind);
        adminPredictionService.recordView(adminId, request.getRemoteAddr());
        return ApiResponse.ok(rows, PageMeta.of(page, size, total));
    }
}
