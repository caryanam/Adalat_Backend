package com.whatsupmarketplacebackend.service.serviceImpl;

import com.whatsupmarketplacebackend.dto.request.PaymentApprovalRequestDTO;
import com.whatsupmarketplacebackend.dto.request.PurchaseSubscriptionRequestDTO;
import com.whatsupmarketplacebackend.dto.response.ClientSubscriptionResponseDTO;
import com.whatsupmarketplacebackend.dto.response.PaymentHistoryResponseDTO;
import com.whatsupmarketplacebackend.dto.response.SubscriptionUsageResponseDTO;
import com.whatsupmarketplacebackend.entity.Client;
import com.whatsupmarketplacebackend.entity.ClientSubscription;
import com.whatsupmarketplacebackend.entity.PaymentHistory;
import com.whatsupmarketplacebackend.entity.SubscriptionPlan;
import com.whatsupmarketplacebackend.enums.PaymentStatus;
import com.whatsupmarketplacebackend.enums.SubscriptionStatus;
import com.whatsupmarketplacebackend.exception.ResourceNotFoundException;
import com.whatsupmarketplacebackend.exception.SubscriptionException;
import com.whatsupmarketplacebackend.repository.ClientRepository;
import com.whatsupmarketplacebackend.repository.ClientSubscriptionRepository;
import com.whatsupmarketplacebackend.repository.PaymentHistoryRepository;
import com.whatsupmarketplacebackend.repository.SubscriptionPlanRepository;
import com.whatsupmarketplacebackend.service.ClientSubscriptionService;
import com.whatsupmarketplacebackend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClientSubscriptionServiceImpl implements ClientSubscriptionService {

    private final ClientSubscriptionRepository clientSubscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final ClientRepository clientRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final NotificationService notificationService;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public ClientSubscriptionResponseDTO purchaseSubscription(Long clientId, PurchaseSubscriptionRequestDTO request) {

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));

        SubscriptionPlan plan = planRepository.findById(request.getPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));

        if (!plan.getIsActive()) {
            throw new SubscriptionException("This plan is currently disabled");
        }

        // Check if there's already a pending payment or active subscription
        Optional<ClientSubscription> activeSub = clientSubscriptionRepository.findByClientIdAndSubscriptionStatus(clientId, SubscriptionStatus.ACTIVE);
        if (activeSub.isPresent()) {
            throw new SubscriptionException("You already have an active subscription. Please use upgrade instead.");
        }

        Optional<ClientSubscription> pendingSub = clientSubscriptionRepository.findByClientIdAndSubscriptionStatus(clientId, SubscriptionStatus.PAYMENT_PENDING);
        if (pendingSub.isPresent()) {
            throw new SubscriptionException("You already have a pending subscription purchase.");
        }

        // Create new subscription
        ClientSubscription subscription = new ClientSubscription();
        subscription.setClient(client);
        subscription.setSubscriptionPlan(plan);
        subscription.setPaymentStatus(PaymentStatus.PENDING);
        subscription.setSubscriptionStatus(SubscriptionStatus.PAYMENT_PENDING);
        subscription.setAmount(plan.getPrice());
        subscription.setPurchaseDate(LocalDateTime.now());

        ClientSubscription savedSubscription = clientSubscriptionRepository.save(subscription);

        // Create payment history
        createPaymentHistory(savedSubscription, request.getPaymentMethod());

        // Send Notifications
        notificationService.sendNotification(client.getEmail(), "Subscription Purchase Pending",
                "You have requested to purchase the " + plan.getPlanName() + " plan. Awaiting Admin verification.", com.whatsupmarketplacebackend.enums.NotificationType.PAYMENT);
        notificationService.sendToAllAdmins("New Subscription Purchase",
                "Client " + client.getOwnerName() + " has purchased the " + plan.getPlanName() + " plan. Payment requires verification.", com.whatsupmarketplacebackend.enums.NotificationType.PAYMENT);

        return mapToResponse(savedSubscription);
    }

    @Override
    @Transactional
    public ClientSubscriptionResponseDTO upgradeSubscription(Long clientId, PurchaseSubscriptionRequestDTO request) {

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));

        SubscriptionPlan plan = planRepository.findById(request.getPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));

        if (!plan.getIsActive()) {
            throw new SubscriptionException("This plan is currently disabled");
        }

        // Ensure there is an active subscription to upgrade from
        ClientSubscription activeSub = clientSubscriptionRepository.findByClientIdAndSubscriptionStatus(clientId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new SubscriptionException("No active subscription found to upgrade. Please purchase a new plan instead."));

        // Cannot upgrade to same plan
        if (activeSub.getSubscriptionPlan().getId().equals(plan.getId())) {
            throw new SubscriptionException("You are already on this plan.");
        }

        // Create new pending subscription
        ClientSubscription newSubscription = new ClientSubscription();
        newSubscription.setClient(client);
        newSubscription.setSubscriptionPlan(plan);
        newSubscription.setPaymentStatus(PaymentStatus.PENDING);
        newSubscription.setSubscriptionStatus(SubscriptionStatus.PAYMENT_PENDING);
        newSubscription.setAmount(plan.getPrice());
        newSubscription.setPurchaseDate(LocalDateTime.now());

        ClientSubscription savedSubscription = clientSubscriptionRepository.save(newSubscription);

        // Create payment history
        createPaymentHistory(savedSubscription, request.getPaymentMethod());

        // Send Notifications
        notificationService.sendNotification(client.getEmail(), "Subscription Upgrade Pending",
                "You have requested to upgrade to the " + plan.getPlanName() + " plan. Awaiting Admin verification.", com.whatsupmarketplacebackend.enums.NotificationType.SUBSCRIPTION);
        notificationService.sendToAllAdmins("New Subscription Upgrade",
                "Client " + client.getOwnerName() + " requested an upgrade to the " + plan.getPlanName() + " plan. Payment requires verification.", com.whatsupmarketplacebackend.enums.NotificationType.SUBSCRIPTION);

        return mapToResponse(savedSubscription);
    }

    private void createPaymentHistory(ClientSubscription subscription, String paymentMethod) {
        PaymentHistory payment = new PaymentHistory();
        payment.setClientSubscription(subscription);
        payment.setAmount(subscription.getAmount());
        payment.setPaymentMethod(paymentMethod);
        payment.setStatus(PaymentStatus.PENDING);
        paymentHistoryRepository.save(payment);
    }

    @Override
    public ClientSubscriptionResponseDTO getCurrentSubscription(Long clientId) {
        return clientSubscriptionRepository.findByClientIdAndSubscriptionStatus(clientId, SubscriptionStatus.ACTIVE)
                .map(this::mapToResponse)
                .orElseThrow(() -> new SubscriptionException("No active subscription found"));
    }

    @Override
    public List<ClientSubscriptionResponseDTO> getSubscriptionHistory(Long clientId) {
        return clientSubscriptionRepository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public SubscriptionUsageResponseDTO getSubscriptionUsage(Long clientId) {
        ClientSubscription activeSub = clientSubscriptionRepository.findByClientIdAndSubscriptionStatus(clientId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new SubscriptionException("No active subscription found"));

        SubscriptionPlan plan = activeSub.getSubscriptionPlan();

        SubscriptionUsageResponseDTO usage = new SubscriptionUsageResponseDTO();
        usage.setClientId(clientId);
        usage.setPlanName(plan.getPlanName() + " (" + plan.getPlanCode() + ")");
        usage.setTotalMessagesAllowed(plan.getMessageLimit());
        usage.setRemainingMessages(activeSub.getRemainingMessages());
        usage.setMessagesUsed(plan.getMessageLimit() - activeSub.getRemainingMessages());

        usage.setTotalCampaignsAllowed(plan.getCampaignLimit());
        usage.setCampaignsUsed(activeSub.getCampaignUsed());
        usage.setRemainingCampaigns(plan.getCampaignLimit() - activeSub.getCampaignUsed());

        long days = ChronoUnit.DAYS.between(LocalDateTime.now(), activeSub.getExpiryDate());
        usage.setDaysRemaining(days > 0 ? days + " days" : "Expires today");

        return usage;
    }

    @Override
    public List<PaymentHistoryResponseDTO> getPaymentHistory(Long clientId) {
        return paymentHistoryRepository.findByClientSubscription_ClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(p -> {
                    PaymentHistoryResponseDTO dto = modelMapper.map(p, PaymentHistoryResponseDTO.class);
                    dto.setPlanName(p.getClientSubscription().getSubscriptionPlan().getPlanName());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<ClientSubscriptionResponseDTO> getAllSubscriptions() {
        return clientSubscriptionRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<PaymentHistoryResponseDTO> getAllPayments() {
        return paymentHistoryRepository.findAll().stream()
                .map(p -> {
                    PaymentHistoryResponseDTO dto = modelMapper.map(p, PaymentHistoryResponseDTO.class);
                    dto.setPlanName(p.getClientSubscription().getSubscriptionPlan().getPlanName());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<PaymentHistoryResponseDTO> getPendingPayments() {
        return paymentHistoryRepository.findByStatusOrderByCreatedAtDesc(PaymentStatus.PENDING).stream()
                .map(p -> {
                    PaymentHistoryResponseDTO dto = modelMapper.map(p, PaymentHistoryResponseDTO.class);
                    dto.setPlanName(p.getClientSubscription().getSubscriptionPlan().getPlanName());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ClientSubscriptionResponseDTO approveOrRejectPayment(Long paymentId, String adminEmail, PaymentApprovalRequestDTO request) {
        PaymentHistory payment = paymentHistoryRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new SubscriptionException("Payment is already " + payment.getStatus());
        }

        if (request.getStatus() != PaymentStatus.APPROVED && request.getStatus() != PaymentStatus.REJECTED) {
            throw new SubscriptionException("Status must be APPROVED or REJECTED");
        }

        ClientSubscription subscription = payment.getClientSubscription();
        SubscriptionPlan plan = subscription.getSubscriptionPlan();

        // Update Payment
        payment.setStatus(request.getStatus());
        payment.setApprovedBy(adminEmail);
        payment.setApprovedAt(LocalDateTime.now());
        payment.setRemarks(request.getRemarks());
        paymentHistoryRepository.save(payment);

        if (request.getStatus() == PaymentStatus.APPROVED) {
            // Mark old active subscription as UPGRADED if this is an upgrade
            Optional<ClientSubscription> oldActiveSub = clientSubscriptionRepository.findByClientIdAndSubscriptionStatus(
                    subscription.getClient().getId(), SubscriptionStatus.ACTIVE);

            oldActiveSub.ifPresent(oldSub -> {
                oldSub.setSubscriptionStatus(SubscriptionStatus.UPGRADED);
                clientSubscriptionRepository.save(oldSub);
            });

            // Update Subscription to ACTIVE
            subscription.setPaymentStatus(PaymentStatus.APPROVED);
            subscription.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
            subscription.setApprovedDate(LocalDateTime.now());
            subscription.setExpiryDate(LocalDateTime.now().plusDays(plan.getValidityDays()));
            subscription.setRemainingMessages(plan.getMessageLimit());
            subscription.setCampaignUsed(0);

            notificationService.sendNotification(subscription.getClient().getEmail(), "Payment Approved",
                    "Your payment has been verified. The " + plan.getPlanName() + " plan is now active!", com.whatsupmarketplacebackend.enums.NotificationType.SUBSCRIPTION);
        } else {
            // Update Subscription to REJECTED
            subscription.setPaymentStatus(PaymentStatus.REJECTED);
            subscription.setSubscriptionStatus(SubscriptionStatus.REJECTED);

            notificationService.sendNotification(subscription.getClient().getEmail(), "Payment Rejected",
                    "Your payment verification failed. Reason: " + request.getRemarks(), com.whatsupmarketplacebackend.enums.NotificationType.PAYMENT);
        }

        ClientSubscription savedSubscription = clientSubscriptionRepository.save(subscription);

        return mapToResponse(savedSubscription);
    }

    private ClientSubscriptionResponseDTO mapToResponse(ClientSubscription subscription) {
        return modelMapper.map(subscription, ClientSubscriptionResponseDTO.class);
    }
}
