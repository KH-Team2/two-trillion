package com.hoperefund.common.exception;

import com.hoperefund.common.dto.ApiResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;

/**
 * 공통 API 예외 처리 클래스. 모든 오류를 공통 봉투 {@code {"success": false, "error": {"code", "message"}}}로 통일한다
 * (ApiResponse · 팀 합의 11/09).
 *
 * <p>보안: 오류 원인(스택 트레이스 · DB 주소 · SQL)은 서버 로그에만 남기고 응답에는 넣지 않는다. 로그: 사용자 실수(400 · 404 등)는 warn, 서버
 * 문제(500 · 503)는 error. 금액 · 생년월일 · 토큰 같은 입력값은 로그에 넣지 않는다.
 *
 * <p>errorId: 서버 쪽 오류(500 · 503)에만 짧은 추적 번호를 만들어 응답과 로그에 같이 남긴다. 사용자가 번호를 알려주면 로그에서 바로 찾는다.
 *
 * <p>TODO: 인증·권한(Spring Security) 적용 후 401 · 403 처리 정리
 */
@RestControllerAdvice // 여러 Controller에서 발생한 예외를 공통 처리
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---------- 업무 오류 (service에서 던지는 예외) ----------

    // 입력값 규칙 위반 (예: 자녀 수 > 부양가족 수) — @Valid 실패와 같은 코드 VALIDATION
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException e) {
        log.warn("잘못된 요청: {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION", e.getMessage()));
    }

    // 기준값이 없는 연도 (예: 2024) — BadRequestException보다 먼저 잡혀 NO_CRITERIA로 나간다
    @ExceptionHandler(NoCriteriaException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoCriteria(NoCriteriaException e) {
        log.warn("기준값이 없는 연도 요청");
        return ResponseEntity.badRequest().body(ApiResponse.fail("NO_CRITERIA", e.getMessage()));
    }

    // 계산 서버에 연결할 수 없을 때 — 원인은 로그에만, 화면에는 다시 시도 안내
    @ExceptionHandler(CalcUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleCalcUnavailable(CalcUnavailableException e) {
        String errorId = newErrorId();
        log.error("[errorId={}] 계산 서버 연결 실패", errorId, e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE) // 503
                .body(
                        ApiResponse.fail(
                                "CALC_UNAVAILABLE",
                                "계산 서버에 잠시 연결할 수 없어요. 잠시 후 다시 시도해 주세요",
                                errorId));
    }

    // 계산 서버가 입력값을 거부했을 때 — 다시 시도해도 같으니 입력 확인 안내
    @ExceptionHandler(CalcRejectedException.class)
    public ResponseEntity<ApiResponse<Void>> handleCalcRejected(CalcRejectedException e) {
        log.warn("계산 서버가 입력값을 거부: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail("CALC_REJECTED", "계산할 수 없는 입력값이 있어요. 소득 정보를 확인해 주세요"));
    }

    // 같은 데이터를 동시에 두 번 저장해 겹쳤을 때 (예: 저장 버튼 두 번 클릭)
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException e) {
        log.warn("동시 저장 충돌: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT) // 409
                .body(ApiResponse.fail("CONFLICT", "저장이 겹쳤어요. 잠시 후 다시 저장해 주세요"));
    }

    // 조회한 데이터가 없을 때
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFoundData(NotFoundException e) {
        log.warn("데이터 없음: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("NOT_FOUND", e.getMessage()));
    }

    // ---------- 요청 형식 오류 (Spring이 던지는 예외) ----------

    // 요청 데이터 유효성 검증 오류 (@Valid 실패) — 첫 번째 칸의 안내 문구를 보여준다
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        FieldError first = e.getBindingResult().getFieldError();
        String message = first != null ? first.getDefaultMessage() : "입력값을 확인해 주세요.";
        // 칸 이름만 남긴다 (입력한 값은 남기지 않음)
        log.warn("입력값 검사 실패: {}", first != null ? first.getField() : "-");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST) // 400 입력 오류
                .body(ApiResponse.fail("VALIDATION", message));
    }

    // JSON 형식 오류 (예: 숫자 칸에 글자)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidJson(HttpMessageNotReadableException e) {
        log.warn("JSON 형식 오류");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("VALIDATION", "요청 데이터 형식을 확인해 주세요."));
    }

    // 주소의 연도나 회원번호 헤더가 숫자가 아닐 때 (예: /api/incomes/abc)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e) {
        log.warn("요청 값 형식 오류: {}", e.getName());
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail("VALIDATION", "요청 값 형식이 올바르지 않아요"));
    }

    // 회원번호 헤더(X-Member-Id)가 없는 요청 — 로그인 적용 전까지의 처리
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException e) {
        log.warn("회원 정보 없는 요청 (헤더 {} 없음)", e.getHeaderName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED) // 401
                .body(ApiResponse.fail("UNAUTHORIZED", "로그인이 필요해요"));
    }

    // 존재하지 않는 정적 리소스 또는 API 경로
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND) // 404 조회 주소 없음
                .body(ApiResponse.fail("NOT_FOUND", "요청한 주소를 찾을 수 없어요."));
    }

    // 허용하지 않는 요청 방식 (예: 계산 주소에 GET)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED) // 405
                .body(ApiResponse.fail("METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식이에요"));
    }

    // 처리하지 못한 서버 오류 (DB 접속 실패 등)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        // 민감정보가 포함될 가능성이 있는 상세 예외 메시지는 응답에 넣지 않고 로그에만 남긴다
        String errorId = newErrorId();
        log.error("[errorId={}] 처리하지 못한 오류", errorId, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR) // 500 서버 오류
                .body(ApiResponse.fail("SERVER_ERROR", "잠시 후 다시 시도해 주세요.", errorId));
    }

    /** 오류 추적 번호 8자리 (예: a3f9c2d1) · 추측할 수 있는 순번이 아니라 무작위 값 */
    private static String newErrorId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
