package com.adalat.enums;

import lombok.Getter;

@Getter
public enum LegalCategory {
    PROPERTY_RENTAL_DISPUTE("Property & Rental Dispute", PracticeArea.PROPERTY_LAW),
    DIVORCE("Divorce & Matrimonial Dispute", PracticeArea.MATRIMONIAL_MATTERS),
    CRIMINAL_MATTER("Criminal Offence & FIR", PracticeArea.CRIMINAL_LAW),
    WORKPLACE_ISSUE("Workplace & Labour Issue", PracticeArea.EMPLOYMENT_LAW),
    CONSUMER_COMPLAINT("Consumer Grievance & Complaint", PracticeArea.CONSUMER_LAW),
    CYBERCRIME("Cybercrime & Online Fraud", PracticeArea.CYBERCRIME),
    FAMILY_DISPUTE("Family & Partition Dispute", PracticeArea.FAMILY_LAW),
    CIVIL_DISPUTE("Civil Dispute & Recovery", PracticeArea.CIVIL_DISPUTES),
    MATRIMONIAL_MATTER("Matrimonial & Maintenance", PracticeArea.MATRIMONIAL_MATTERS),
    BANKING_FINANCE("Banking, Loan & Financial Dispute", PracticeArea.BANKING_AND_FINANCE),
    EMPLOYMENT_DISPUTE("Employment & Termination Dispute", PracticeArea.EMPLOYMENT_LAW),
    CORPORATE_MATTER("Corporate & Commercial Matter", PracticeArea.CORPORATE_LAW),
    OTHER("General Legal Inquiry", PracticeArea.CIVIL_DISPUTES);

    private final String displayName;
    private final PracticeArea defaultPracticeArea;

    LegalCategory(String displayName, PracticeArea defaultPracticeArea) {
        this.displayName = displayName;
        this.defaultPracticeArea = defaultPracticeArea;
    }
}
