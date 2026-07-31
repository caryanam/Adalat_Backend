package com.whatsupmarketplacebackend.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportSummaryDTO {

    private int totalRowsRead;
    private int imported;
    private int duplicate;
    private int invalid;
    private int skipped;
    private int failed;
    private String importBatchId;
}
