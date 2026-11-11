package com.hoperefund.income.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.hoperefund.common.dto.ErrorResponseDTO;
import com.hoperefund.income.controller.IncomeController;

import lombok.extern.slf4j.Slf4j;

/**
 * 소득 API 전용 오류 처리
 *
 * <p>왜 따로 두나: 공통 GlobalExceptionHandler(common/)에는 아직 업무 오류(404 · 409 등) 처리가 없어서, 그대로 두면
 * 전부 500으로 나간다. 공통 파일은 고치지 않고(팀 규칙), 소득 API에서 나는 오류만 여기서 처리한다.
 *
 * <p>- assignableTypes: IncomeController에서 난 오류만 맡는다 (다른 기능에는 영향 없음)
 *
 * <p>- @Order(HIGHEST_PRECEDENCE): 공통 처리기보다 먼저 확인한다. 여기 없는 오류(예: @Valid 검사 실패)는 공통 처리기로
 * 넘어간다
 *
 * <p>응답 모양은 공통과 같은 ErrorResponseDTO {"error": 코드, "message": 문구}
 */
@Slf4j // log 변수 자동 생성 (Lombok)
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = IncomeController.class)
public class IncomeExceptionHandler {

    /** Service가 던진 업무 오류 (404 소득 없음 · 400 연도 · 자녀 수) */
    @ExceptionHandler(IncomeException.class)
    public ResponseEntity<ErrorResponseDTO> handleIncome(IncomeException e) {
        return ResponseEntity.status(e.getStatus())
                .body(new ErrorResponseDTO(e.getCode(), e.getMessage()));
    }

    /** 같은 회원 · 연도의 첫 저장이 동시에 두 번 와서 UNIQUE 제약(UQ_INCOME_MEMBER_YEAR)에 걸림 → 409 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponseDTO> handleDuplicate(DuplicateKeyException e) {
        log.warn("소득 동시 저장 충돌"); // 원인은 로그에만 · 금액은 남기지 않음 (백엔드 규칙)
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponseDTO("CONFLICT", "다른 곳에서 동시에 저장했어요. 다시 시도해 주세요."));
    }

    /** 회원번호 헤더(X-Member-Id)가 없음 → 401 (로그인 연결 전 개발용 헤더 · 백엔드 API 규칙) */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponseDTO> handleNoMember(MissingRequestHeaderException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponseDTO("UNAUTHORIZED", "로그인이 필요해요."));
    }

    /** 주소의 연도나 헤더 값이 숫자가 아님 (예: /api/incomes/abc) → 400 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handleTypeMismatch(
            MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponseDTO("VALIDATION", "입력값을 확인해 주세요."));
    }
}
