package com.adalat.enums;

import lombok.Getter;

@Getter
public enum ConsultationRate {
    FREE(0),
    RATE_99(99),
    RATE_149(149),
    RATE_199(199),
    RATE_299(299),
    RATE_499(499);

    private final int amount;

    ConsultationRate(int amount) {
        this.amount = amount;
    }
}