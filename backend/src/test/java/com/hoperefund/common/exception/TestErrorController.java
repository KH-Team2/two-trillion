package com.hoperefund.common.exception;

import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 공통 예외 처리 테스트를 위한 전용 Controller
 * 잘못된 JSON과 서버 내부 예외 상황을 재현한다.
 * 실제 서비스 API로 등록하지 않는다.
 */
@RestController
public class TestErrorController {

    @PostMapping("/api/test-errors/json")
    public String invalidJson(@RequestBody TestRequest request) {
        return request.content();
    }

    @GetMapping("/api/test-errors/server")
    public String serverError() {
        throw new IllegalStateException("테스트용 오류");
    }

    // 필수 쿼리 값(year)이 있는 조회
    @GetMapping("/api/test-errors/param")
    public String requiredParam(@RequestParam("year") int year) {
        return String.valueOf(year);
    }

    // 쿼리 값에 검증 규칙(1 이상)이 있는 조회
    @GetMapping("/api/test-errors/page")
    public String pageParam(@RequestParam("page") @Min(1) int page) {
        return String.valueOf(page);
    }

    // 파일(file)이 꼭 있어야 하는 업로드
    @PostMapping("/api/test-errors/upload")
    public String upload(@RequestParam("file") MultipartFile file) {
        return "ok";
    }

    // 업로드 크기 초과 상황 재현 (실제 크기 검사는 서버(톰캣)가 해서 테스트에서는 예외를 직접 던진다)
    @PostMapping("/api/test-errors/too-large")
    public String tooLarge() {
        throw new MaxUploadSizeExceededException(1024);
    }

    // 코드에서 상태 코드를 직접 정해 던지는 경우 (권한 없음 403)
    @GetMapping("/api/test-errors/status")
    public String statusError() {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없어요");
    }

    // 상태 코드를 직접 정해 던졌는데 서버 쪽 오류(502)인 경우 — 원인 문구가 응답에 나가면 안 된다
    @GetMapping("/api/test-errors/status-server")
    public String statusServerError() {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "내부 서버 연결 실패");
    }

    public record TestRequest(String content) {}
}
