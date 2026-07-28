package com.whatsupmarketplacebackend.service.serviceImpl;

import com.whatsupmarketplacebackend.dto.EnquiryRequestDTO;
import com.whatsupmarketplacebackend.dto.EnquiryResponseDTO;
import com.whatsupmarketplacebackend.entity.Enquiry;
import com.whatsupmarketplacebackend.exception.ResourceNotFoundException;
import com.whatsupmarketplacebackend.repository.EnquiryRepository;
import com.whatsupmarketplacebackend.service.EnquiryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnquiryServiceImpl implements EnquiryService {

    private final EnquiryRepository enquiryRepository;

    @Override
    public EnquiryResponseDTO createEnquiry(EnquiryRequestDTO requestDTO) {
        log.info("Creating new enquiry for email: {}", requestDTO.email());
        
        Enquiry enquiry = Enquiry.builder()
                .name(requestDTO.name())
                .phoneNumber(requestDTO.phoneNumber())
                .email(requestDTO.email())
                .goals(requestDTO.goals())
                .build();
                
        Enquiry savedEnquiry = enquiryRepository.save(enquiry);
        
        return EnquiryResponseDTO.builder()
                .id(savedEnquiry.getId())
                .name(savedEnquiry.getName())
                .phoneNumber(savedEnquiry.getPhoneNumber())
                .email(savedEnquiry.getEmail())
                .goals(savedEnquiry.getGoals())
                .createdAt(savedEnquiry.getCreatedAt())
                .build();
    }

    @Override
    public List<EnquiryResponseDTO> getAllEnquiries() {
        log.info("Fetching all enquiries");
        return enquiryRepository.findAll().stream()
                .map(enquiry -> EnquiryResponseDTO.builder()
                        .id(enquiry.getId())
                        .name(enquiry.getName())
                        .phoneNumber(enquiry.getPhoneNumber())
                        .email(enquiry.getEmail())
                        .goals(enquiry.getGoals())
                        .createdAt(enquiry.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public EnquiryResponseDTO getEnquiryById(Long id) {
        log.info("Fetching enquiry by id: {}", id);
        Enquiry enquiry = enquiryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enquiry not found with id: " + id));
        return EnquiryResponseDTO.builder()
                .id(enquiry.getId())
                .name(enquiry.getName())
                .phoneNumber(enquiry.getPhoneNumber())
                .email(enquiry.getEmail())
                .goals(enquiry.getGoals())
                .createdAt(enquiry.getCreatedAt())
                .build();
    }


}
