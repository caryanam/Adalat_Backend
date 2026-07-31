package com.whatsupmarketplacebackend.dto.request;

import com.whatsupmarketplacebackend.enums.VariableType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VariableMappingDTO {

    @NotNull(message = "Variable index is required")
    private Integer variableIndex;

    @NotNull(message = "Variable type is required")
    private VariableType variableType;

    /**
     * For DYNAMIC: customer data field name (customer_name, city, state, business_name, whatsapp_number)
     */
    private String fieldName;

    /**
     * For STATIC: literal value to use for all recipients
     */
    private String staticValue;
}
