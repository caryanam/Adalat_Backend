package com.whatsupmarketplacebackend.dto;

import com.whatsupmarketplacebackend.entity.ImportLog;
import java.time.LocalDateTime;

public record ImportLogResponseDTO(
        int totalRowsRead,
        int totalImported,
        int skippedEmptyRows,
        int skippedInvalidRows,
        int skippedDuplicateRows,
        LocalDateTime importedAt
) {
    public static ImportLogResponseDTO from(ImportLog log) {
        return new ImportLogResponseDTO(
                log.getTotalRowsRead(),
                log.getTotalImported(),
                log.getSkippedEmptyRows(),
                log.getSkippedInvalidRows(),
                log.getSkippedDuplicateRows(),
                log.getImportedAt()
        );
    }
}
