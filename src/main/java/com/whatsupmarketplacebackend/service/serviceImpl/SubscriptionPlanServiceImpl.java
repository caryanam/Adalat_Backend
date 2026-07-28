package com.whatsupmarketplacebackend.service.serviceImpl;

import com.whatsupmarketplacebackend.dto.request.PlanRequestDTO;
import com.whatsupmarketplacebackend.dto.response.PlanResponseDTO;
import com.whatsupmarketplacebackend.entity.SubscriptionPlan;
import com.whatsupmarketplacebackend.exception.ResourceNotFoundException;
import com.whatsupmarketplacebackend.exception.SubscriptionException;
import com.whatsupmarketplacebackend.repository.SubscriptionPlanRepository;
import com.whatsupmarketplacebackend.service.SubscriptionPlanService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionPlanServiceImpl implements SubscriptionPlanService {

    private final SubscriptionPlanRepository planRepository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public PlanResponseDTO createPlan(PlanRequestDTO request) {
        
        Optional<SubscriptionPlan> existingPlan = planRepository.findByPlanCode(request.getPlanCode());
        if (existingPlan.isPresent()) {
            throw new SubscriptionException("A plan with code " + request.getPlanCode() + " already exists.");
        }
        
        SubscriptionPlan plan = modelMapper.map(request, SubscriptionPlan.class);
        SubscriptionPlan savedPlan = planRepository.save(plan);
        return modelMapper.map(savedPlan, PlanResponseDTO.class);
    }

    @Override
    @Transactional
    public PlanResponseDTO updatePlan(Long id, PlanRequestDTO request) {
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with id: " + id));
        
        // Prevent changing plan code to an existing one that belongs to another plan
        if (!plan.getPlanCode().equals(request.getPlanCode())) {
            Optional<SubscriptionPlan> existingPlan = planRepository.findByPlanCode(request.getPlanCode());
            if (existingPlan.isPresent()) {
                throw new SubscriptionException("A plan with code " + request.getPlanCode() + " already exists.");
            }
        }
        
        plan.setPlanName(request.getPlanName());
        plan.setPlanCode(request.getPlanCode());
        plan.setPlanType(request.getPlanType());
        plan.setPrice(request.getPrice());
        plan.setMessageLimit(request.getMessageLimit());
        plan.setCampaignLimit(request.getCampaignLimit());
        plan.setValidityDays(request.getValidityDays());
        
        if (request.getIsActive() != null) {
            plan.setIsActive(request.getIsActive());
        }

        SubscriptionPlan updatedPlan = planRepository.save(plan);
        return modelMapper.map(updatedPlan, PlanResponseDTO.class);
    }

    @Override
    @Transactional
    public void deletePlan(Long id) {
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with id: " + id));
        
        plan.setIsActive(false);
        planRepository.save(plan);
    }

    @Override
    public PlanResponseDTO getPlanById(Long id) {
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with id: " + id));
        return modelMapper.map(plan, PlanResponseDTO.class);
    }

    @Override
    public List<PlanResponseDTO> getAllPlans() {
        return planRepository.findAll().stream()
                .map(plan -> modelMapper.map(plan, PlanResponseDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    public List<PlanResponseDTO> getActivePlans() {
        return planRepository.findByIsActiveTrue().stream()
                .map(plan -> modelMapper.map(plan, PlanResponseDTO.class))
                .collect(Collectors.toList());
    }
}
