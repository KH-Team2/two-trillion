package com.hoperefund.income.controller;

import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.income.dto.IncomeRequestDTO;
import com.hoperefund.income.dto.IncomeResponseDTO;
import com.hoperefund.income.service.IncomeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소득·공제 정보 API (F-06 · F-02-01)
 *
 * <p>- GET /api/incomes/{taxYear} : 조회 → 200 · 400 · 404
 *
 * <p>- PUT /api/incomes/{taxYear} : 저장 → 200 · 400 · 409
 *
 * <p>Controller는 요청 받기 · 응답 보내기만 하고, 검사 · 저장은 Service가 한다 (백엔드 규칙)
 *
 * <p>[수정 11/11] 응답을 팀 공통 봉투 ApiResponse로 감쌈 {"success": true, "data": 소득} · 이유: develop에 공통
 * 응답(PR #6)이 들어와서 팀 전체 응답 모양을 맞춤. 오류 응답은 공통 GlobalExceptionHandler가 만듦
 *
 * <p>회원번호: 로그인 기능이 붙기 전까지는 개발용 헤더 X-Member-Id로 받는다 (백엔드 API 규칙 · 주소에는 안 넣음)
 */
@RestController // 반환값을 화면 이동이 아니라 JSON 응답으로 보냄
@RequiredArgsConstructor
@RequestMapping("/api/incomes") // 이 클래스의 모든 주소 앞에 /api/incomes가 붙음
public class IncomeController {

    private final IncomeService incomeService;

    /** 소득 조회 */
    @GetMapping("/{taxYear}")
    public ApiResponse<IncomeResponseDTO> getIncome(
            @RequestHeader("X-Member-Id") long memberId, // 요청 헤더에서 회원번호 꺼내기
            @PathVariable("taxYear") int taxYear) { // 주소의 {taxYear} 부분 꺼내기
        return ApiResponse.ok(incomeService.getIncome(memberId, taxYear)); // 200 + 소득
    }

    /** 소득 저장 (있으면 수정, 없으면 등록) */
    @PutMapping("/{taxYear}")
    public ApiResponse<IncomeResponseDTO> saveIncome(
            @RequestHeader("X-Member-Id") long memberId,
            @PathVariable("taxYear") int taxYear,
            // @RequestBody: 본문 JSON → DTO로 바꿔 줌
            // @Valid: DTO의 검사 어노테이션(@NotNull 등)을 실행. 어기면 여기 들어오기 전에 400
            @Valid @RequestBody IncomeRequestDTO request) {
        return ApiResponse.ok(incomeService.saveIncome(memberId, taxYear, request));
    }
}
