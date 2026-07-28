package com.whatsupmarketplacebackend.service.serviceImpl;

import com.whatsupmarketplacebackend.dto.response.AdminAnalyticsResponseDTO;
import com.whatsupmarketplacebackend.entity.Campaign;
import com.whatsupmarketplacebackend.enums.PaymentStatus;
import com.whatsupmarketplacebackend.enums.SubscriptionStatus;
import com.whatsupmarketplacebackend.repository.CampaignRepository;
import com.whatsupmarketplacebackend.repository.ClientSubscriptionRepository;
import com.whatsupmarketplacebackend.repository.PaymentHistoryRepository;
import com.whatsupmarketplacebackend.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final ClientSubscriptionRepository clientSubscriptionRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final CampaignRepository campaignRepository;

    @Override
    public AdminAnalyticsResponseDTO getAdminAnalytics() {
        AdminAnalyticsResponseDTO analytics = new AdminAnalyticsResponseDTO();
        
        long activeCount = clientSubscriptionRepository.findAll().stream()
                .filter(sub -> sub.getSubscriptionStatus() == SubscriptionStatus.ACTIVE)
                .count();
        analytics.setTotalActiveSubscriptions(activeCount);
        
        long expiredCount = clientSubscriptionRepository.findAll().stream()
                .filter(sub -> sub.getSubscriptionStatus() == SubscriptionStatus.EXPIRED)
                .count();
        analytics.setTotalExpiredSubscriptions(expiredCount);
        
        double revenue = paymentHistoryRepository.findAll().stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.APPROVED)
                .mapToDouble(payment -> payment.getAmount() != null ? payment.getAmount() : 0.0)
                .sum();
        analytics.setTotalRevenue(revenue);
        
        long pending = paymentHistoryRepository.findAll().stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                .count();
        analytics.setPendingPayments(pending);
        
        List<Campaign> campaigns = campaignRepository.findAll();
        analytics.setTotalCampaignsRun((long) campaigns.size());
        
        long messagesSent = campaigns.stream()
                .mapToLong(c -> c.getMessagesSent() != null ? c.getMessagesSent() : 0)
                .sum();
        analytics.setTotalMessagesSent(messagesSent);
        
        return analytics;
    }
}
