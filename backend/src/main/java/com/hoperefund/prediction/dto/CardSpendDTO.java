package com.hoperefund.prediction.dto;

/** 카드 사용 합계 1줄 (EXPENSE를 분류 + 결제수단별로 더한 값 · 계산 서버로 보냄) 취소 건은 음수로 저장돼 있어서 합계에서 자동으로 빠져요. */
public class CardSpendDTO {

    private Long
            categoryId; // SPEND_CATEGORY.CATEGORY_ID (1 일반 · 2 대중교통 · 3 전통시장 · 4 문화체육 · 5 공제 대상 아님)
    private String payMethod; // CREDIT · CHECK · CASH
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
