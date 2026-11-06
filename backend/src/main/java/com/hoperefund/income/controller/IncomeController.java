package com.hoperefund.income.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.hoperefund.income.dto.IncomeResponseDTO;
import com.hoperefund.income.service.IncomeService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/incomes")
public class IncomeController {
	
	/* service DI */
	private final IncomeService incomeService;
	
	
	/* ------------- 화면 이동 요청 --------------- */
	
	/**
	 * 소득·공제 정보 조회
	 * @param taxYear
	 * @ return
	 */
	@GetMapping("/{taxYear}")
	public ResponseEntity<IncomeResponseDTO> getIncome(@PathVariable int taxYear) {
		return null;
	}
	
	/**
	 * 소득·공제 정보 저장 (2025·2026만, totalSalary·withheldTax 필수)
	 * @param
	 */
	@PutMapping("/{taxYear}")
	public ResponseEntity<IncomeResponseDTO> saveIncome(@PathVariable int taxYear,
				@RequestBody IncomeRequestDTO request) {
		return null;
	}
}
