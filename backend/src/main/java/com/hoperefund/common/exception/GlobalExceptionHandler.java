package com.hoperefund.common.exception;

import com.hoperefund.common.dto.ApiResponse;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 공통 API 예외 처리 클래스. 모든 오류를 공통 봉투 {@code {"success": false, "error": {"code", "message",
 * "errorId"}}}로 통일한다 (ApiResponse · 팀 합의 11/09). errorId는 500 · 503에만 있다.
 *
 * <p>보안: 오류 원인(스택 트레이스 · DB 주소 · SQL)은 서버 로그에만 남기고 응답에는 넣지 않는다. 로그: 사용자 실수(400 · 404 등)는 warn, 서버
 * 문제(500 · 503)는 error. 금액 · 생년월일 · 토큰 같은 입력값은 로그에 넣지 않는다.
 *
 * <p>errorId: 서버 쪽 오류(500 · 503)에만 짧은 추적 번호(UUID 앞 8자리 · 매번 새로 만듦)를 응답과 로그에 같이 남긴다. 사용자가 번호를 알려주면
 * 로그에서 바로 찾는다. 사용자 쪽 오류(400 · 401 · 403 · 404 · 405 · 409 · 413 · 415)엔 붙이지 않는다.
 *
 * <p>남의 데이터 요청은 403이 아니라 404(없는 것과 같은 응답)로 보낸다 — 번호를 바꿔 가며 남의 데이터가 있는지 알아낼 수 없게.
 *
 * <p>TODO: 인증·권한(Spring Security) 적용 후 401 · 403 처리 정리
 */
@RestControllerAdvice // 여러 Controller에서 발생한 예외를 공통 처리
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---------- 업무 오류 (service에서 던지는 예외) ----------

    // 상태 · 코드를 직접 정해 던지는 업무 오류 (예: 회원가입 DUPLICATE_LOGIN_ID 409 · UNDER_AGE 400)
    // 서버 쪽 상태(5xx)면 문구는 숨기고 SERVER_ERROR + errorId (ResponseStatusException과 같은 기준)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        HttpStatus status = e.getStatus();
        if (status.is5xxServerError()) {
            String errorId = newErrorId();
            log.error("[errorId={}] 업무 처리 서버 오류 ({})", errorId, e.getCode(), e);
            return ResponseEntity.status(status)
                    .body(ApiResponse.fail("SERVER_ERROR", "잠시 후 다시 시도해 주세요.", errorId));
        }
        log.warn("업무 규칙 오류: {} ({})", e.getCode(), status.value());
        return ResponseEntity.status(status).body(ApiResponse.fail(e.getCode(), e.getMessage()));
    }

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

    // 본문 형식(Content-Type)이 JSON이 아님 (예: text/plain으로 PUT) — 사용자 요청 문제라 500이 아니라 415
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException e) {
        log.warn("지원하지 않는 본문 형식: {}", e.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE) // 415
                .body(ApiResponse.fail("UNSUPPORTED_MEDIA_TYPE", "JSON 형식으로 보내 주세요"));
    }

    // 필수 쿼리 값이 빠졌을 때 (예: ?year= 없이 목록 호출) — 사용자 요청 문제라 500이 아니라 400
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
            MissingServletRequestParameterException e) {
        log.warn("필수 요청 값 없음: {}", e.getParameterName());
        return ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION", "필수 요청 값이 빠졌어요"));
    }

    // 쿼리 · 경로 값이 검증 규칙(@Min 등)에 걸렸을 때 — 입력한 값은 로그에 남기지 않는다
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(
            HandlerMethodValidationException e) {
        log.warn("요청 값 검사 실패");
        return ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION", "요청 값을 확인해 주세요"));
    }

    // 파일 업로드 요청에 파일이 없을 때
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(
            MissingServletRequestPartException e) {
        log.warn("업로드 파일 없음: {}", e.getRequestPartName());
        return ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION", "파일을 선택해 주세요"));
    }

    // 업로드 파일이 허용 크기를 넘었을 때 — 다시 시도해도 같으니 500이 아니라 413
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(
            MaxUploadSizeExceededException e) {
        log.warn("업로드 파일 크기 초과");
        return ResponseEntity.status(413)
                .body(ApiResponse.fail("PAYLOAD_TOO_LARGE", "파일이 너무 커요. 더 작은 파일로 올려 주세요"));
    }

    // 코드에서 상태 코드를 직접 정해 던진 예외 — 정한 상태 그대로 내보낸다 (500으로 뭉개지지 않게)
    // 서버 쪽 상태(5xx)면 원인 문구는 로그에만 남기고 errorId를 붙인다
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException e) {
        int status = e.getStatusCode().value();
        if (e.getStatusCode().is5xxServerError()) {
            String errorId = newErrorId();
            log.error("[errorId={}] 서버 오류 응답 ({})", errorId, status, e);
            return ResponseEntity.status(status)
                    .body(ApiResponse.fail("SERVER_ERROR", "잠시 후 다시 시도해 주세요.", errorId));
        }
        log.warn("요청 오류 응답 ({})", status);
        String message = e.getReason() != null ? e.getReason() : "요청을 처리할 수 없어요";
        return ResponseEntity.status(status).body(ApiResponse.fail(codeOf(status), message));
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

    /** 상태 코드에 맞는 오류 코드 이름 (docs/api-rules.md 5번 표와 같은 이름) */
    private static String codeOf(int status) {
        return switch (status) {
            case 400 -> "VALIDATION";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 409 -> "CONFLICT";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "REQUEST_ERROR";
        };
    }

    /** 오류 추적 번호 8자리 (예: a3f9c2d1) · 추측할 수 있는 순번이 아니라 무작위 값 */
    private static String newErrorId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
