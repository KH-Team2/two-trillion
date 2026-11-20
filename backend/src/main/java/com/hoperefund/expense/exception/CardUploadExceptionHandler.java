package com.hoperefund.expense.exception;

import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.expense.controller.CardUploadController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * CardUploadException → 공통 응답 봉투 {"success": false, "error": {"code", "message"}}
 *
 * <p>왜 공통 GlobalExceptionHandler에 안 넣었나: common/은 공통 영역이라 승인 없이 고치지 않는다 (백엔드 규칙). 그래서
 * expense 폴더 안에 업로드 전용 처리기를 따로 둔다.
 *
 * <p>assignableTypes: CardUploadController에서 난 오류만 여기서 처리
 *
 * <p>@Order(HIGHEST_PRECEDENCE): 공통 처리기보다 먼저 확인 (공통 처리기의 Exception.class가 먼저 잡아서 500이 되지 않게)
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = CardUploadController.class)
public class CardUploadExceptionHandler {

    @ExceptionHandler(CardUploadException.class)
    public ResponseEntity<ApiResponse<Void>> handleCardUpload(CardUploadException e) {
        // 사용자 쪽 오류라 warn · 코드만 남김 (파일 내용 · 금액은 로그에 안 남김)
        log.warn("카드 업로드 거부: {}", e.getCode());
        return ResponseEntity.status(e.getStatus())
                .body(ApiResponse.fail(e.getCode(), e.getMessage()));
    }
}
