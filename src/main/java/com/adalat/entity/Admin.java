package com.adalat.entity;

import com.adalat.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "adminReg")
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long adminId;

    private String fullName;
    private String mobileNumber;

    @Column(unique = true, nullable = false)
    private String email;

    private String password;


    public void setRole(Role role) {
    }
}
