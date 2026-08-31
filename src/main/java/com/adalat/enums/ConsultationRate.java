package com.adalat.enums;

import lombok.Getter;

@Getter
public enum ConsultationRate {
    RATE_99(99),
    RATE_149(149),
    RATE_199(199);

    private final int amount;

    ConsultationRate(int amount) {
        this.amount = amount;
    }
}
