package com.hoperefund.expense.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 계산 서버(POST /card/parse)가 돌려주는 거래 1건
 *
 * <p>계산 서버가 파일을 읽어 공통 4칸(승인일시 · 가맹점명 · 이용금액 · 사용구분)으로 바꾸고, 분류까지 붙여서 준다.
 *
 * <p>JSON 예: {"rowNo": 5, "usedDate": "2026-03-02", "merchantName": "서울교통공사", "payMethod":
 * "CREDIT", "amount": 1550, "categoryCode": "TRANSIT", "listId": 1234}
 */
@Data // getter · setter 등 자동 생성 (Lombok)
@NoArgsConstructor // JSON → 객체로 바꿀 때 빈 생성자가 필요함
@AllArgsConstructor
@Builder
public class ParsedExpenseDTO {

    private Integer rowNo; // 파일에서 몇 번째 줄인지 (엑셀에서 보이는 줄 번호)
    private LocalDate usedDate; // 사용일 ("2026-03-02" 글자가 날짜로 자동 변환됨)
    private String merchantName; // 가맹점명 (정제 전 원본)
    private String payMethod; // CREDIT · CHECK · CASH
    private Long amount; // 금액 (취소 건은 음수)
    private String categoryCode; // 분류 코드 GENERAL · TRANSIT · MARKET · CULTURE · EXCLUDED
    private Long listId; // 일치한 공공 목록 번호 (일반이면 null)
}
