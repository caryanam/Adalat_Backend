package com.adalat.dto;

import com.adalat.enums.Language;
import com.adalat.enums.PracticeArea;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LawyerUpdateProfileRequestDTO {

    @jakarta.validation.constraints.Pattern(
        regexp = "^$|^(?=.*[a-zA-Z].*[a-zA-Z].*[a-zA-Z])[a-zA-Z][a-zA-Z\\s.'-]*[a-zA-Z.]+$",
        message = "Full Name must contain at least 3 alphabetic characters and cannot be single letters or dots (e.g. Adv. Rajesh Verma)"
    )
    private String fullName;

    @jakarta.validation.constraints.Pattern(regexp = "^$|^[6-9]\\d{9}$", message = "Mobile number must be a valid 10-digit Indian mobile number")
    private String mobileNumber;

    private String barEnrollmentNumber;

    private Integer yearsOfExperience;

    private String education;

    private String location;

    private Set<PracticeArea> practiceAreas;

    private Set<Language> languages;

    private String bio;

    private Integer consultationFee;

    @jakarta.validation.constraints.Pattern(
        regexp = "^$|^[a-zA-Z0-9._-]{2,256}@[a-zA-Z]{2,64}$",
        message = "UPI ID must be in valid format: username@bankhandle (e.g. name@upi, 9876543210@paytm)"
    )
    private String upiId;

    private String profilePhotoUrl;
}
