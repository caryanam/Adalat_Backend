package com.adalat.enums;

import lombok.Getter;

@Getter
public enum ConsultationRate {
    FREE(0),
    RATE_79(79),
    RATE_99(99),
    RATE_149(149),
    RATE_199(199),
    RATE_200(200),
    RATE_299(299),
    RATE_499(499),
    RATE_999(999),
    CUSTOM(0);

    private final int amount;

    ConsultationRate(int amount) {
        this.amount = amount;
    }

    public static ConsultationRate fromAmount(int amount) {
        for (ConsultationRate r : values()) {
            if (r.amount == amount && r != CUSTOM) return r;
        }
        return CUSTOM;
    }
}