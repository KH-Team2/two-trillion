package com.hoperefund.admin.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * [신규 11/26 고은] F-19 기준값 변경 이력 한 줄 (CRITERIA_HISTORY · v4.4)
 *
 * <p>기준값을 UPDATE 하면 DB 트리거가 「바뀐 칸 하나당 1줄」씩 이전 값 · 새 값을 자동으로 남겨요. 이 상자는 그 줄을
 * 화면에 보여 줄 때 써요 (명세 F-08-01 출력 「수정 이력(수정일 · 수정자)」).
 */
@Getter
@Setter
public class CriteriaHistoryResponseDTO {
    private Long historyId;
    private String columnName; // 바뀐 칸 (RATE · LIMIT_AMOUNT 등)
    private String oldValue; // 이전 값 (NULL = 비어 있었음)
    private String newValue; // 새 값 (NULL = 비움)
    private Long changedBy; // 바꾼 관리자 번호 (NULL = 스크립트로 고침)
    private String changedAt; // 'YYYY-MM-DD HH24:MI'
}
