package com.adalat.enums;

import lombok.Getter;

@Getter
public enum LegalCategory {
    EMPLOYMENT("Employment / Labour", PracticeArea.EMPLOYMENT_LAW),
    EMPLOYMENT_SALARY("Salary / Employment Dispute", PracticeArea.EMPLOYMENT_LAW),
    CRIMINAL("Criminal Offence & FIR", PracticeArea.CRIMINAL_LAW),
    PROPERTY("Property Dispute", PracticeArea.PROPERTY_LAW),
    TENANCY("Tenancy / Rental Dispute", PracticeArea.PROPERTY_LAW),
    FAMILY("Family & Matrimonial", PracticeArea.FAMILY_LAW),
    CONSUMER("Consumer Grievance", PracticeArea.CONSUMER_LAW),
    CYBERCRIME("Cybercrime & Online Fraud", PracticeArea.CYBERCRIME),
    BANKING_FINANCE("Banking, Loan & Financial", PracticeArea.BANKING_AND_FINANCE),
    CONTRACT("Contracts & Agreements", PracticeArea.CIVIL_DISPUTES),
    BUSINESS_COMMERCIAL("Business & Commercial", PracticeArea.CORPORATE_LAW),
    INSURANCE("Insurance Dispute", PracticeArea.CIVIL_DISPUTES),
    MOTOR_VEHICLE("Motor Vehicle Accidents", PracticeArea.CIVIL_DISPUTES),
    CIVIL("Civil Dispute & Recovery", PracticeArea.CIVIL_DISPUTES),
    INTELLECTUAL_PROPERTY("Intellectual Property", PracticeArea.CORPORATE_LAW),
    OTHER_LEGAL("General Legal Inquiry", PracticeArea.CIVIL_DISPUTES);

    private final String displayName;
    private final PracticeArea defaultPracticeArea;

    LegalCategory(String displayName, PracticeArea defaultPracticeArea) {
        this.displayName = displayName;
        this.defaultPracticeArea = defaultPracticeArea;
    }
}
