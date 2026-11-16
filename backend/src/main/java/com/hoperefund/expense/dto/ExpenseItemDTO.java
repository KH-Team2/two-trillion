package com.hoperefund.expense.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 거래 1건 + 분류 근거 (GET /api/expenses · F-09)
 *
 * <p>분류 근거 = 어느 목록(MERCHANT_LIST) 행과 일치해서 이 분류가 됐는지
 *
 * <ul>
 *   <li>matchType: EXACT(이름이 같아서) · CONTAINS(이름 안에 들어 있어서)
 *   <li>matchedName: 일치한 목록의 상호명 (예: 서울교통공사)
 *   <li>sourceName: 그 목록의 출처 데이터셋 (예: 공공데이터포털 ○○ 목록)
 *   <li>일반(GENERAL)으로 분류된 건은 일치한 목록이 없어서 세 칸 모두 null
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseItemDTO {

    private Long expenseId; // 지출번호
    private Long uploadId; // 어느 업로드에서 왔는지
    private LocalDate usedDate; // 사용일 → JSON "2026-03-02"
    private String merchantName; // 가맹점명 (카드 파일 원본 그대로)
    private String payMethod; // CREDIT · CHECK · CASH
    private Long amount; // 금액 (취소 건은 음수)
    private String categoryCode; // 분류 코드 GENERAL · TRANSIT · MARKET · CULTURE · EXCLUDED
    private String categoryName; // 화면에 보여 줄 분류 이름

    // ▼ 분류 근거 (일반이면 전부 null)
    private Long listId; // 일치한 목록 번호
    private String matchType; // EXACT · CONTAINS
    private String matchedName; // 일치한 목록의 상호명
    private String sourceName; // 목록 출처
}
