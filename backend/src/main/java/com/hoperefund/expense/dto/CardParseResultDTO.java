package com.hoperefund.expense.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 계산 서버(POST /card/parse) 성공 응답 전체
 *
 * <p>JSON 예: {"rowCount": 14, "rows": [{...}, {...}]}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CardParseResultDTO {

    private Integer rowCount; // 읽은 거래 건수
    private List<ParsedExpenseDTO> rows; // 거래 목록
}
