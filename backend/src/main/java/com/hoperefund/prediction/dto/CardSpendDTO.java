package com.hoperefund.prediction.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 카드 사용 합계 1줄 (EXPENSE를 분류 + 결제수단별로 더한 값 · 계산 서버로 보냄) 취소 건은 음수로 저장돼 있어서 합계에서 자동으로 빠져요.
 *
 * <p>검사(@NotNull 등)는 시뮬레이션 요청(@Valid)으로 받을 때만 실행돼요. DB에서 읽은 합계(기존 계산)는 검사를 거치지 않아요.
 */
public class CardSpendDTO {

    @NotNull(message = "카드 분류를 골라 주세요")
    @Min(value = 1, message = "카드 분류 번호가 올바르지 않아요")
    @Max(value = 5, message = "카드 분류 번호가 올바르지 않아요")
    private Long
            categoryId; // SPEND_CATEGORY.CATEGORY_ID (1 일반 · 2 대중교통 · 3 전통시장 · 4 문화체육 · 5 공제 대상 아님)

    @NotNull(message = "결제수단을 골라 주세요")
    @Pattern(regexp = "CREDIT|CHECK|CASH", message = "결제수단은 CREDIT · CHECK · CASH 중 하나예요")
    private String payMethod; // CREDIT · CHECK · CASH

    @NotNull(message = "카드 사용액을 입력해 주세요")
    @Min(value = 0, message = "카드 사용액은 0원 이상이어야 해요")
    @Max(value = 1_000_000_000L, message = "금액이 너무 커요")
    private Long amount; // 합계 (원)

    public CardSpendDTO() {}

    public CardSpendDTO(Long categoryId, String payMethod, Long amount) {
        this.categoryId = categoryId;
        this.payMethod = payMethod;
        this.amount = amount;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getPayMethod() {
        return payMethod;
    }

    public void setPayMethod(String payMethod) {
        this.payMethod = payMethod;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }
}
