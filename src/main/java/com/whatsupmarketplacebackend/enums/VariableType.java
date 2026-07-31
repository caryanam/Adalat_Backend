package com.whatsupmarketplacebackend.enums;

/**
 * Variable mapping type for campaign template variables.
 * DYNAMIC: resolved per-recipient from customer data fields (e.g., customer_name)
 * STATIC: same value for all recipients (e.g., offer_date = "31st July")
 */
public enum VariableType {
    DYNAMIC,
    STATIC
}
