package com.adalat.configuration;

import com.adalat.entity.*;
import com.adalat.enums.*;
import com.adalat.repository.AdminRepository;
import com.adalat.repository.LawyerRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final AdminRepository adminRepository;
    private final LawyerRepository lawyerRepository;
    private final com.adalat.repository.CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        log.info("Checking database initialization...");

        // 1. Admin Account
        if (adminRepository.findByEmail("admin@gmail.com").isEmpty()) {
            Admin admin = Admin.builder()
                    .fullName("System Admin")
                    .email("admin@gmail.com")
                    .mobileNumber("9867865788")
                    .password(passwordEncoder.encode("admin@123"))
                    .build();
            adminRepository.save(admin);
        }
        log.info("Default admins seeded.");

        // 1b. Customer Account
        if (customerRepository.count() == 0) {
            Customer customer = Customer.builder()
                    .fullName("Default Customer")
                    .email("customer@gmail.com")
                    .mobileNumber("9876543210")
                    .password(passwordEncoder.encode("Customer@123"))
                    .role(Role.CUSTOMER)
                    .build();
            customerRepository.save(customer);
            log.info("Default Customer seeded.");
        }
        // 2. Seed Verified Advocates if empty
        if (lawyerRepository.count() == 0) {
            log.info("Seeding initial verified advocates into database...");

            Lawyer l1 = Lawyer.builder()
                    .fullName("Adv. Rajesh Sharma")
                    .email("rajesh.sharma@adalat.in")
                    .mobileNumber("9810012345")
                    .password(passwordEncoder.encode("Lawyer@123"))
                    .barEnrollmentNumber("D/1842/2010")
                    .yearsOfExperience(14)
                    .education("LL.M. (Property & Civil Litigation), Campus Law Centre, Delhi University")
                    .location("Delhi High Court & Supreme Court")
                    .practiceAreas(Set.of(PracticeArea.PROPERTY_LAW, PracticeArea.CIVIL_DISPUTES))
                    .languages(Set.of(Language.ENGLISH, Language.HINDI))
                    .bio("Senior Advocate specializing in High Court property disputes, rent control litigation, illegal eviction suits, and title deeds verification with 14+ years of bar experience.")
                    .consultationRate(ConsultationRate.RATE_99)
                    .upiId("rajesh.lawyer@upi")
                    .role(Role.LAWYER)
                    .registrationStatus(RegistrationStatus.SUBMITTED)
                    .verificationStatus(VerificationStatus.APPROVED)
                    .accountStatus(AccountStatus.ACTIVE)
                    .build();

            Lawyer l2 = Lawyer.builder()
                    .fullName("Adv. Meera Deshmukh")
                    .email("meera.deshmukh@adalat.in")
                    .mobileNumber("9820023456")
                    .password(passwordEncoder.encode("Lawyer@123"))
                    .barEnrollmentNumber("MAH/5820/2009")
                    .yearsOfExperience(15)
                    .education("B.A. LL.B. (Hons), Government Law College, Mumbai")
                    .location("Family Court & Bombay High Court")
                    .practiceAreas(Set.of(PracticeArea.FAMILY_LAW, PracticeArea.MATRIMONIAL_MATTERS))
                    .languages(Set.of(Language.ENGLISH, Language.HINDI, Language.MARATHI))
                    .bio("Experienced Matrimonial & Family Advocate specializing in mutual consent divorce, child custody petitions, alimony claims, and domestic violence protection orders.")
                    .consultationRate(ConsultationRate.RATE_99)
                    .upiId("meera.lawyer@upi")
                    .role(Role.LAWYER)
                    .registrationStatus(RegistrationStatus.SUBMITTED)
                    .verificationStatus(VerificationStatus.APPROVED)
                    .accountStatus(AccountStatus.ACTIVE)
                    .build();

            Lawyer l3 = Lawyer.builder()
                    .fullName("Adv. Vikramaditya Singh")
                    .email("vikramaditya@adalat.in")
                    .mobileNumber("9830034567")
                    .password(passwordEncoder.encode("Lawyer@123"))
                    .barEnrollmentNumber("UP/8492/2007")
                    .yearsOfExperience(17)
                    .education("LL.B., Faculty of Law, BHU Varanasi")
                    .location("Supreme Court of India & Allahabad High Court")
                    .practiceAreas(Set.of(PracticeArea.CRIMINAL_LAW, PracticeArea.CYBERCRIME))
                    .languages(Set.of(Language.ENGLISH, Language.HINDI))
                    .bio("Senior Criminal Defense Specialist handling anticipatory bail applications, FIR quashing writs under Sec 482, and financial cyber fraud trial defense.")
                    .consultationRate(ConsultationRate.RATE_299)
                    .upiId("vikram.lawyer@upi")
                    .role(Role.LAWYER)
                    .registrationStatus(RegistrationStatus.SUBMITTED)
                    .verificationStatus(VerificationStatus.APPROVED)
                    .accountStatus(AccountStatus.ACTIVE)
                    .build();

            Lawyer l4 = Lawyer.builder()
                    .fullName("Adv. Rohan Kulkarni")
                    .email("rohan.kulkarni@adalat.in")
                    .mobileNumber("9840045678")
                    .password(passwordEncoder.encode("Lawyer@123"))
                    .barEnrollmentNumber("KA/3910/2011")
                    .yearsOfExperience(13)
                    .education("LL.M. (Labor & Corporate Law), NLSIU Bengaluru")
                    .location("Karnataka High Court & Labor Court")
                    .practiceAreas(Set.of(PracticeArea.EMPLOYMENT_LAW, PracticeArea.CORPORATE_LAW))
                    .languages(Set.of(Language.ENGLISH, Language.HINDI, Language.KANNADA))
                    .bio("Labor Law specialist representing corporate employees and executives in unpaid salary recovery, severance disputes, non-compete notices, and workplace harassment claims.")
                    .consultationRate(ConsultationRate.RATE_99)
                    .upiId("rohan.lawyer@upi")
                    .role(Role.LAWYER)
                    .registrationStatus(RegistrationStatus.SUBMITTED)
                    .verificationStatus(VerificationStatus.APPROVED)
                    .accountStatus(AccountStatus.ACTIVE)
                    .build();

            Lawyer l5 = Lawyer.builder()
                    .fullName("Adv. Ananya Iyer")
                    .email("ananya.iyer@adalat.in")
                    .mobileNumber("9850056789")
                    .password(passwordEncoder.encode("Lawyer@123"))
                    .barEnrollmentNumber("MAH/3920/2014")
                    .yearsOfExperience(11)
                    .education("B.A. LL.B., ILS Law College, Pune")
                    .location("Bombay High Court & Consumer Disputes Redressal Commission")
                    .practiceAreas(Set.of(PracticeArea.CONSUMER_LAW, PracticeArea.CIVIL_DISPUTES, PracticeArea.PROPERTY_LAW))
                    .languages(Set.of(Language.ENGLISH, Language.HINDI, Language.TAMIL))
                    .bio("Civil & Consumer Law advocate expert in builder compensation claims under RERA, defective product refund disputes, and contract breach summary suits.")
                    .consultationRate(ConsultationRate.RATE_99)
                    .upiId("ananya.lawyer@upi")
                    .role(Role.LAWYER)
                    .registrationStatus(RegistrationStatus.SUBMITTED)
                    .verificationStatus(VerificationStatus.APPROVED)
                    .accountStatus(AccountStatus.ACTIVE)
                    .build();

            Lawyer l6 = Lawyer.builder()
                    .fullName("Adv. Suresh Verma")
                    .email("suresh.verma@adalat.in")
                    .mobileNumber("9860067890")
                    .password(passwordEncoder.encode("Lawyer@123"))
                    .barEnrollmentNumber("D/4910/2012")
                    .yearsOfExperience(12)
                    .education("LL.B., Faculty of Law, University of Delhi")
                    .location("Delhi High Court & District Courts")
                    .practiceAreas(Set.of(PracticeArea.BANKING_AND_FINANCE, PracticeArea.CIVIL_DISPUTES))
                    .languages(Set.of(Language.ENGLISH, Language.HINDI))
                    .bio("Banking & Debt Recovery Specialist in Section 138 Cheque Bounce notices, loan recovery defense, and arbitration proceedings.")
                    .consultationRate(ConsultationRate.RATE_99)
                    .upiId("suresh.lawyer@upi")
                    .role(Role.LAWYER)
                    .registrationStatus(RegistrationStatus.SUBMITTED)
                    .verificationStatus(VerificationStatus.APPROVED)
                    .accountStatus(AccountStatus.ACTIVE)
                    .build();

            lawyerRepository.saveAll(List.of(l1, l2, l3, l4, l5, l6));
            log.info("Successfully seeded 6 verified Advocates into database.");
        }
    }
}
