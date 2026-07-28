package com.whatsupmarketplacebackend.scheduler;

import com.whatsupmarketplacebackend.entity.ClientSubscription;
import com.whatsupmarketplacebackend.enums.SubscriptionStatus;
import com.whatsupmarketplacebackend.repository.ClientSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpiryScheduler {

    private final ClientSubscriptionRepository subscriptionRepository;

    // Runs every hour
    @Scheduled(cron = "0 0 * * * *")
    public void expireSubscriptions() {
        log.info("Running SubscriptionExpiryScheduler...");
        
        List<ClientSubscription> expiredSubscriptions = subscriptionRepository
                .findBySubscriptionStatusAndExpiryDateBefore(SubscriptionStatus.ACTIVE, LocalDateTime.now());
                
        if (expiredSubscriptions.isEmpty()) {
            log.info("No subscriptions to expire.");
            return;
        }

        for (ClientSubscription subscription : expiredSubscriptions) {
            subscription.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
            log.info("Expired subscription ID: {} for Client ID: {}", subscription.getId(), subscription.getClient().getId());
        }

        subscriptionRepository.saveAll(expiredSubscriptions);
        log.info("Successfully expired {} subscriptions.", expiredSubscriptions.size());
    }
}
