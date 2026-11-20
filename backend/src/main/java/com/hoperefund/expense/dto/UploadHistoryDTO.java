package com.hoperefund.expense.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 업로드 이력 1줄 (GET /api/card-uploads · F-09)
 *
 * <p>JSON 예: {"uploadId": 10, "fileName": "3월_신한.xlsx", "rowCount": 120, "uploadedAt":
 * "2026-11-14T18:40:00"}
 *
 * <p>CardUploadDTO(테이블 한 행 전체)와 따로 둔 이유: 회원번호 · 데이터 구분처럼 화면에 필요 없는 칸은 응답에 안 보내려고
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadHistoryDTO {

    private Long uploadId; // 업로드번호
    private String fileName; // 원본 파일명
    private Integer rowCount; // 거래 건수
    private LocalDateTime uploadedAt; // 업로드일
}
