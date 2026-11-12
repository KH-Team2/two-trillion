package com.hoperefund.expense.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 카드 업로드 성공 응답 (POST /api/card-uploads · API 명세서)
 *
 * <p>화면은 이 값으로 「N건 업로드 · 분류 완료」와 분류별 건수를 보여 준다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardUploadResponseDTO {

    private Long uploadId; // 업로드번호
    private String fileName; // 원본 파일명
    private Integer rowCount; // 저장한 거래 건수
    private LocalDateTime uploadedAt; // 업로드일
    private List<CategoryCountDTO> categoryCounts; // 분류별 건수
}
