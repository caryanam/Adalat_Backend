package com.whatsupmarketplacebackend.service.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsupmarketplacebackend.dto.CustomerDataResponseDTO;
import com.whatsupmarketplacebackend.dto.ExcelImportResponseDTO;
import com.whatsupmarketplacebackend.dto.response.ImportSummaryDTO;
import com.whatsupmarketplacebackend.entity.Client;
import com.whatsupmarketplacebackend.entity.CustomerData;
import com.whatsupmarketplacebackend.entity.ImportLog;
import com.whatsupmarketplacebackend.repository.ClientRepository;
import com.whatsupmarketplacebackend.repository.CustomerDataRepository;
import com.whatsupmarketplacebackend.repository.ImportLogRepository;
import com.whatsupmarketplacebackend.service.CustomerDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Enhanced customer data import service with support for:
 * - Customer Name, Mobile Number (columns 0-1, required)
 * - City, State, Business Name (columns 2-4, optional)
 * - Any Dynamic Fields (columns 5+, stored as JSON)
 * - Mobile number validation (10-digit Indian format)
 * - Duplicate detection (within batch + against existing DB records)
 * - Import batch tracking
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CustomerDataServiceImpl implements CustomerDataService {

    // Standard columns
    private static final int NAME_COLUMN_INDEX = 0;
    private static final int WHATSAPP_COLUMN_INDEX = 1;
    private static final int CITY_COLUMN_INDEX = 2;
    private static final int STATE_COLUMN_INDEX = 3;
    private static final int BUSINESS_NAME_COLUMN_INDEX = 4;
    private static final int DYNAMIC_FIELDS_START = 5;
    private static final int HEADER_ROW_INDEX = 0;

    // Indian mobile: 10 digits starting with 6-9
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^[6-9]\\d{9}$");

    private final CustomerDataRepository customerDataRepository;
    private final ImportLogRepository importLogRepository;
    private final ClientRepository clientRepository;
    private final ObjectMapper objectMapper;

    @Override
    public ExcelImportResponseDTO importFromExcel(MultipartFile file, Long clientId, com.whatsupmarketplacebackend.enums.BusinessCategory businessCategory) {

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found with ID: " + clientId));

        validateFile(file);

        String importBatchId = UUID.randomUUID().toString();

        int totalRowsRead = 0;
        int skippedEmptyRows = 0;
        int skippedInvalidRows = 0;
        int skippedDuplicateRows = 0;
        int failedRows = 0;

        List<CustomerData> customersToSave = new ArrayList<>();
        Set<String> seenWhatsappNumbers = new HashSet<>();

        // Fetch existing numbers to avoid duplicates
        Set<String> existingNumbers = customerDataRepository.findByClientId(clientId).stream()
                .map(CustomerData::getWhatsappNumber)
                .collect(Collectors.toSet());

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();
            // Parse header row for dynamic field column names
            List<String> dynamicFieldNames = parseDynamicFieldHeaders(sheet.getRow(HEADER_ROW_INDEX));

            for (int rowIndex = HEADER_ROW_INDEX + 1; rowIndex <= lastRowNum; rowIndex++) {

                Row row = sheet.getRow(rowIndex);

                if (isRowEmpty(row)) {
                    skippedEmptyRows++;
                    continue;
                }

                totalRowsRead++;

                String customerName = getCellValueAsString(row.getCell(NAME_COLUMN_INDEX));
                String whatsappNumber = getCellValueAsString(row.getCell(WHATSAPP_COLUMN_INDEX));

                // Validate name
                if (customerName.isBlank()) {
                    log.warn("Skipping row {} - missing customer name", rowIndex + 1);
                    skippedInvalidRows++;
                    continue;
                }

                // Validate mobile number
                if (whatsappNumber.isBlank() || !isValidMobileNumber(whatsappNumber)) {
                    log.warn("Skipping row {} - invalid mobile number: {}", rowIndex + 1, whatsappNumber);
                    skippedInvalidRows++;
                    continue;
                }

                // Check for duplicate whatsapp number (in DB or within same batch)
                if (existingNumbers.contains(whatsappNumber) || !seenWhatsappNumbers.add(whatsappNumber)) {
                    log.info("Skipping duplicate row {} with WhatsApp number {}", rowIndex + 1, whatsappNumber);
                    skippedDuplicateRows++;
                    continue;
                }

                // Parse optional fields
                String city = getCellValueAsString(row.getCell(CITY_COLUMN_INDEX));
                String state = getCellValueAsString(row.getCell(STATE_COLUMN_INDEX));
                String businessName = getCellValueAsString(row.getCell(BUSINESS_NAME_COLUMN_INDEX));

                // Parse dynamic fields (columns 5+)
                String dynamicFieldsJson = parseDynamicFields(row, dynamicFieldNames);

                try {
                    customersToSave.add(
                            CustomerData.builder()
                                    .customerName(customerName)
                                    .whatsappNumber(whatsappNumber)
                                    .client(client)
                                    .businessCategory(businessCategory)
                                    .city(city.isBlank() ? null : city)
                                    .state(state.isBlank() ? null : state)
                                    .businessName(businessName.isBlank() ? null : businessName)
                                    .dynamicFields(dynamicFieldsJson)
                                    .importBatchId(importBatchId)
                                    .build()
                    );
                } catch (Exception e) {
                    log.error("Failed to process row {}", rowIndex + 1, e);
                    failedRows++;
                }
            }

        } catch (IOException e) {
            log.error("Failed to read excel file", e);
            throw new IllegalArgumentException("Unable to read the uploaded excel file. Please upload a valid .xlsx or .xls file.");
        }

        // Batch insert
        List<CustomerData> savedCustomers = new ArrayList<>();
        if (!customersToSave.isEmpty()) {
            savedCustomers = customerDataRepository.saveAll(customersToSave);
        }

        log.info("Excel import completed for client {}. Batch: {}. Read: {}, Imported: {}, Empty: {}, Invalid: {}, Duplicate: {}, Failed: {}",
                clientId, importBatchId, totalRowsRead, savedCustomers.size(),
                skippedEmptyRows, skippedInvalidRows, skippedDuplicateRows, failedRows);

        // Save import log
        ImportLog importLog = ImportLog.builder()
                .client(client)
                .totalRowsRead(totalRowsRead)
                .totalImported(savedCustomers.size())
                .skippedEmptyRows(skippedEmptyRows)
                .skippedInvalidRows(skippedInvalidRows)
                .skippedDuplicateRows(skippedDuplicateRows)
                .build();
        importLogRepository.save(importLog);

        List<CustomerDataResponseDTO> responseDTOs = savedCustomers.stream()
                .map(CustomerDataResponseDTO::from)
                .collect(Collectors.toList());

        return new ExcelImportResponseDTO(
                totalRowsRead,
                savedCustomers.size(),
                skippedEmptyRows,
                skippedInvalidRows,
                skippedDuplicateRows,
                responseDTOs
        );
    }

    /**
     * Get import summary for a batch.
     */
    public ImportSummaryDTO getImportSummary(Long clientId) {
        return importLogRepository.findFirstByClientIdOrderByImportedAtDesc(clientId)
                .map(importLog -> ImportSummaryDTO.builder()
                        .totalRowsRead(importLog.getTotalRowsRead())
                        .imported(importLog.getTotalImported())
                        .duplicate(importLog.getSkippedDuplicateRows())
                        .invalid(importLog.getSkippedInvalidRows())
                        .skipped(importLog.getSkippedEmptyRows())
                        .failed(0)
                        .build())
                .orElse(null);
    }

    @Override
    public List<CustomerDataResponseDTO> getCustomerDataByClientId(Long clientId) {
        return customerDataRepository.findByClientId(clientId).stream()
                .map(CustomerDataResponseDTO::from)
                .collect(Collectors.toList());
    }

    public com.whatsupmarketplacebackend.dto.ImportLogResponseDTO getLatestImportStatByClientId(Long clientId) {
        return importLogRepository.findFirstByClientIdOrderByImportedAtDesc(clientId)
                .map(com.whatsupmarketplacebackend.dto.ImportLogResponseDTO::from)
                .orElse(null);
    }

    // ============ Mobile Number Validation ============

    private boolean isValidMobileNumber(String number) {
        if (number == null) return false;
        // Remove common prefixes
        String cleaned = number.replaceAll("[\\s\\-\\+]", "");
        if (cleaned.startsWith("91") && cleaned.length() == 12) {
            cleaned = cleaned.substring(2);
        }
        if (cleaned.startsWith("0") && cleaned.length() == 11) {
            cleaned = cleaned.substring(1);
        }
        return MOBILE_PATTERN.matcher(cleaned).matches();
    }



    // ============ File & Cell Utilities ============

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please upload a non-empty excel file.");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null ||
                !(fileName.toLowerCase().endsWith(".xlsx") || fileName.toLowerCase().endsWith(".xls"))) {
            throw new IllegalArgumentException("Invalid file type. Only .xlsx or .xls files are supported.");
        }
    }    // ============ Dynamic Field Parsing ============

    private List<String> parseDynamicFieldHeaders(Row headerRow) {
        List<String> dynamicHeaders = new ArrayList<>();
        if (headerRow == null) return dynamicHeaders;

        for (int i = DYNAMIC_FIELDS_START; i < headerRow.getLastCellNum(); i++) {
            String header = getCellValueAsString(headerRow.getCell(i));
            dynamicHeaders.add(header.isBlank() ? "field_" + i : header.trim());
        }
        return dynamicHeaders;
    }

    private String parseDynamicFields(Row row, List<String> fieldNames) {
        if (fieldNames.isEmpty()) return null;

        Map<String, String> dynamicMap = new LinkedHashMap<>();
        for (int i = 0; i < fieldNames.size(); i++) {
            int cellIndex = DYNAMIC_FIELDS_START + i;
            String value = getCellValueAsString(row.getCell(cellIndex));
            if (!value.isBlank()) {
                dynamicMap.put(fieldNames.get(i), value);
            }
        }

        if (dynamicMap.isEmpty()) return null;

        try {
            return objectMapper.writeValueAsString(dynamicMap);
        } catch (Exception e) {
            log.warn("Failed to serialize dynamic fields", e);
            return null;
        }
    }    private boolean isRowEmpty(Row row) {
        if (row == null) {
            return true;
        }
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK &&
                    !getCellValueAsString(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType cellType = cell.getCellType();
        if (cellType == CellType.FORMULA) {
            cellType = cell.getCachedFormulaResultType();
        }
        return switch (cellType) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double numericValue = cell.getNumericCellValue();
                if (numericValue == Math.floor(numericValue) && !Double.isInfinite(numericValue)) {
                    yield String.valueOf((long) numericValue);
                }
                yield String.valueOf(numericValue);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }
}
