package com.hoperefund.expense.controller;

import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.expense.dto.CardUploadResponseDTO;
import com.hoperefund.expense.service.CardUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 카드 내역 업로드 API (F-07 · F-02-02 · API 명세서)
 *
 * <p>- POST /api/card-uploads : 업로드 → 200 · 400 · 409 · 503
 *
 * <p>요청 형식: multipart/form-data (파일과 글자 값을 한 요청에 같이 보내는 방식)
 *
 * <ul>
 *   <li>file: 카드사 · 홈택스 파일 (csv · xlsx · xls)
 *   <li>payMethod: (선택) 사용구분 칸이 없는 파일일 때 사용자가 고른 CREDIT / CHECK
 *   <li>allowDuplicate: (선택) 중복 경고 뒤 「계속」을 누르면 true
 * </ul>
 *
 * <p>중복 경고 · 사용구분 선택은 「같은 요청을 한 번 더 보내는」 방식 (API 명세서):
 *
 * <ul>
 *   <li>409 DUPLICATE_WARNING → 화면이 경고 창 → 계속하면 allowDuplicate=true를 붙여 다시 보냄
 *   <li>400 PAY_METHOD_REQUIRED → 화면이 신용/체크 선택 창 → payMethod를 붙여 다시 보냄
 * </ul>
 *
 * <p>회원번호: 로그인 기능이 붙기 전까지는 개발용 헤더 X-Member-Id로 받는다 (소득 API와 같음)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/card-uploads")
public class CardUploadController {

    private final CardUploadService cardUploadService;

    /** 카드 내역 업로드 (consumes: multipart 요청만 받음) */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CardUploadResponseDTO> upload(
            @RequestHeader("X-Member-Id") long memberId,
            // @RequestPart: multipart 요청에서 "file"이라는 이름의 파일 조각을 꺼냄 · 없으면 400 (공통 처리기)
            @RequestPart("file") MultipartFile file,
            // @RequestParam(required = false): 없어도 되는 값 · 없으면 null
            @RequestParam(value = "payMethod", required = false) String payMethod,
            // defaultValue: 안 보내면 false
            @RequestParam(value = "allowDuplicate", defaultValue = "false")
                    boolean allowDuplicate) {
        return ApiResponse.ok(cardUploadService.upload(memberId, file, payMethod, allowDuplicate));
    }
}
