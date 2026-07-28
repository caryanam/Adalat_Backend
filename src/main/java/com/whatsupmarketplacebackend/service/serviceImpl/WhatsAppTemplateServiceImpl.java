package com.whatsupmarketplacebackend.service.serviceImpl;

import com.whatsupmarketplacebackend.dto.WhatsAppTemplateDTO;
import com.whatsupmarketplacebackend.dto.WhatsAppTemplateVariableDTO;
import com.whatsupmarketplacebackend.service.WhatsAppTemplateService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class WhatsAppTemplateServiceImpl implements WhatsAppTemplateService {

    private final List<WhatsAppTemplateDTO> templatesList = Collections.synchronizedList(new ArrayList<>());

    @PostConstruct
    public void initDefaultTemplates() {
        if (templatesList.isEmpty()) {
            populateDefaults();
        }
    }

    @Override
    public List<WhatsAppTemplateDTO> getAllTemplates() {
        return new ArrayList<>(templatesList);
    }

    @Override
    public WhatsAppTemplateDTO createTemplate(WhatsAppTemplateDTO request) {
        long nextId = templatesList.size() + 1L;
        request.setId(nextId);
        if (request.getCategory() == null || request.getCategory().isBlank()) {
            request.setCategory("MARKETING");
        }
        if (request.getHeaderType() == null || request.getHeaderType().isBlank()) {
            request.setHeaderType("TEXT");
        }
        if (request.getVariables() == null) {
            request.setVariables(new ArrayList<>());
        }
        templatesList.add(request);
        return request;
    }

    private void populateDefaults() {

        // 1. Festive Special Offer (With Banner Image)
        templatesList.add(new WhatsAppTemplateDTO(
                1L,
                "Festive Special Offer (With Banner Image)",
                "MARKETING",
                "IMAGE",
                "Hello {{1}}! ðŸ‘‹\n\n{{2}}\n\nðŸ”¥ Special Offer: {{3}}\nâ° Valid Till: {{4}}\n\n{{5}}",
                "https://images.unsplash.com/photo-1563720223185-11003d516935?w=800&auto=format&fit=crop&q=60",
                Arrays.asList(
                        new WhatsAppTemplateVariableDTO("var1", "Customer Name Placeholder", "e.g. {{Customer Name}}", "Customer"),
                        new WhatsAppTemplateVariableDTO("var2", "Headline / Announcement", "e.g. Special Festive Discount Event!", null),
                        new WhatsAppTemplateVariableDTO("var3", "Offer Details", "e.g. Flat 30% OFF on all packages", null),
                        new WhatsAppTemplateVariableDTO("var4", "Expiry / Validity Date", "e.g. Sunday, 31st October", null),
                        new WhatsAppTemplateVariableDTO("var5", "Call-to-Action / Address", "e.g. Visit store or call +91 9811122201", null)
                )
        ));

        // 2. Exclusive Announcement (Text Only)
        templatesList.add(new WhatsAppTemplateDTO(
                2L,
                "Exclusive Announcement (Text Only)",
                "MARKETING",
                "TEXT",
                "Dear {{1}},\n\n{{2}}\n\nâ­ Key Highlights:\nâ€¢ {{3}}\nâ€¢ {{4}}\n\nReply YES to book or contact us directly!",
                null,
                Arrays.asList(
                        new WhatsAppTemplateVariableDTO("var1", "Customer Name Placeholder", "e.g. {{Customer Name}}", "Valued Customer"),
                        new WhatsAppTemplateVariableDTO("var2", "Main Promo Message", "e.g. Exclusive VIP Early Access is now Live!", null),
                        new WhatsAppTemplateVariableDTO("var3", "Highlight Point 1", "e.g. Zero processing fee on all bookings", null),
                        new WhatsAppTemplateVariableDTO("var4", "Highlight Point 2", "e.g. Complimentary doorstep delivery included", null)
                )
        ));

        // 3. Automotive / Car Exchange Fair (With Image)
        templatesList.add(new WhatsAppTemplateDTO(
                3L,
                "Automotive / Car Exchange Fair (With Image)",
                "MARKETING",
                "IMAGE",
                "Greetings {{1}}! ðŸš—\n\n{{2}}\n\nðŸ’° Bonus: {{3}}\nðŸ—“ï¸ Date: {{4}}\n\nðŸ“ {{5}}",
                "https://images.unsplash.com/photo-1549399542-7e3f8b79c341?w=800&auto=format&fit=crop&q=60",
                Arrays.asList(
                        new WhatsAppTemplateVariableDTO("var1", "Customer Name", "e.g. {{Customer Name}}", "Car Enthusiast"),
                        new WhatsAppTemplateVariableDTO("var2", "Event Name / Headline", "e.g. Mega Used Car Exchange Carnival!", null),
                        new WhatsAppTemplateVariableDTO("var3", "Exchange Bonus Value", "e.g. Get up to â‚¹40,000 extra exchange value", null),
                        new WhatsAppTemplateVariableDTO("var4", "Event Schedule / Dates", "e.g. This Weekend (Saturday & Sunday)", null),
                        new WhatsAppTemplateVariableDTO("var5", "Showroom Location & Contact", "e.g. AutoZone Motors, MG Road Metro Pillar 102", null)
                )
        ));

        // 4. Healthcare / Full Body Checkup Camp (With Image)
        templatesList.add(new WhatsAppTemplateDTO(
                4L,
                "Healthcare / Full Body Checkup Camp (With Image)",
                "MARKETING",
                "IMAGE",
                "Dear {{1}}, ðŸ©º\n\n{{2}}\n\nðŸ”¬ Package Includes: {{3}}\nðŸ’µ Special Price: {{4}}\n\n{{5}}",
                "https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?w=800&auto=format&fit=crop&q=60",
                Arrays.asList(
                        new WhatsAppTemplateVariableDTO("var1", "Patient / Customer Name", "e.g. {{Customer Name}}", "Patient"),
                        new WhatsAppTemplateVariableDTO("var2", "Health Camp Title", "e.g. Preventative Health Checkup Month", null),
                        new WhatsAppTemplateVariableDTO("var3", "Included Tests", "e.g. 60+ Essential Blood, Thyroid & Lipid Tests", null),
                        new WhatsAppTemplateVariableDTO("var4", "Discount Price", "e.g. â‚¹999 Only (Regular â‚¹2,499)", null),
                        new WhatsAppTemplateVariableDTO("var5", "Hospital Contact / Home Sample", "e.g. Call City Care Hospital at +91 9000045521", null)
                )
        ));

        // 5. Real Estate Property Pre-Launch VIP Pass
        templatesList.add(new WhatsAppTemplateDTO(
                5L,
                "Real Estate Property Pre-Launch VIP Pass",
                "MARKETING",
                "IMAGE",
                "Hello {{1}}! ðŸ¡\n\n{{2}}\n\nâœ¨ Unit Config: {{3}}\nðŸŽ VIP Perks: {{4}}\n\n{{5}}",
                "https://images.unsplash.com/photo-1560518883-ce09059eeffa?w=800&auto=format&fit=crop&q=60",
                Arrays.asList(
                        new WhatsAppTemplateVariableDTO("var1", "Buyer Name", "e.g. {{Customer Name}}", "Investor"),
                        new WhatsAppTemplateVariableDTO("var2", "Property Project Name", "e.g. Skyline Luxury Towers Pre-Launch", null),
                        new WhatsAppTemplateVariableDTO("var3", "Apartment Types", "e.g. 2, 3 & 4 BHK Luxury Residences", null),
                        new WhatsAppTemplateVariableDTO("var4", "Pre-Launch Benefit", "e.g. Zero Stamp Duty + Free Modular Kitchen", null),
                        new WhatsAppTemplateVariableDTO("var5", "Site Visit Details", "e.g. Book private site tour: +91 9988766211", null)
                )
        ));

        // 6. Garage / Auto Service Reminder (Text Only)
        templatesList.add(new WhatsAppTemplateDTO(
                6L,
                "Garage / Auto Service Reminder (Text Only)",
                "UTILITY",
                "TEXT",
                "Hi {{1}}, ðŸ”§\n\n{{2}}\n\nðŸ› ï¸ Package: {{3}}\nðŸšš Benefit: {{4}}\n\nContact us to confirm your preferred slot!",
                null,
                Arrays.asList(
                        new WhatsAppTemplateVariableDTO("var1", "Owner Name", "e.g. {{Customer Name}}", "Vehicle Owner"),
                        new WhatsAppTemplateVariableDTO("var2", "Service Nudge Message", "e.g. Your car's periodic maintenance service is due!", null),
                        new WhatsAppTemplateVariableDTO("var3", "Service Features", "e.g. Full Oil Change, Filter Replacements & 40-Point Check", null),
                        new WhatsAppTemplateVariableDTO("var4", "Added Convenience", "e.g. Free Pick-up & Drop Facility Available", null)
                )
        ));

        // 7. Hotel & Resort Weekend Getaway (With Image)
        templatesList.add(new WhatsAppTemplateDTO(
                7L,
                "Hotel & Resort Weekend Getaway (With Image)",
                "MARKETING",
                "IMAGE",
                "Dear {{1}}! ðŸ¨\n\n{{2}}\n\nðŸŒ´ Special Package: {{3}}\nðŸ· Inclusions: {{4}}\n\n{{5}}",
                "https://images.unsplash.com/photo-1566073771259-6a8506099945?w=800&auto=format&fit=crop&q=60",
                Arrays.asList(
                        new WhatsAppTemplateVariableDTO("var1", "Guest Name", "e.g. {{Customer Name}}", "Guest"),
                        new WhatsAppTemplateVariableDTO("var2", "Getaway Headline", "e.g. Escape the City Noise at BlueOak Luxury Resort!", null),
                        new WhatsAppTemplateVariableDTO("var3", "Stay Offer Price", "e.g. 2 Nights Weekend Package starting at â‚¹7,999", null),
                        new WhatsAppTemplateVariableDTO("var4", "Free Inclusions", "e.g. Complimentary Breakfast, Spa Voucher & Late Checkout", null),
                        new WhatsAppTemplateVariableDTO("var5", "Reservation Desk Contact", "e.g. Call +91 9021055521 or reply RESERVE", null)
                )
        ));

        // 8. School / College Admission Notification (Text Only)
        templatesList.add(new WhatsAppTemplateDTO(
                8L,
                "School / College Admission Notification (Text Only)",
                "UTILITY",
                "TEXT",
                "Respected Parent / {{1}},\n\n{{2}}\n\nðŸŽ“ Courses: {{3}}\nðŸ“… Last Date: {{4}}\n\nVisit campus or apply online today!",
                null,
                Arrays.asList(
                        new WhatsAppTemplateVariableDTO("var1", "Recipient Name", "e.g. {{Customer Name}}", "Parent"),
                        new WhatsAppTemplateVariableDTO("var2", "Admission Announcement", "e.g. Admissions Open for Academic Session 2026-27!", null),
                        new WhatsAppTemplateVariableDTO("var3", "Grades / Streams Available", "e.g. Pre-Nursery to Grade XII (Science, Commerce, Arts)", null),
                        new WhatsAppTemplateVariableDTO("var4", "Application Deadline", "e.g. 15th November 2026", null)
                )
        ));

        // 9. Finance / Loan Instant Approval Alert (With Image)
        templatesList.add(new WhatsAppTemplateDTO(
                9L,
                "Finance / Loan Instant Approval Alert (With Image)",
                "MARKETING",
                "IMAGE",
                "Hi {{1}}! ðŸ’°\n\n{{2}}\n\nðŸ“‰ Interest Rate: {{3}}\nâš¡ Approval Time: {{4}}\n\n{{5}}",
                "https://images.unsplash.com/photo-1559526324-4b87b5e36e44?w=800&auto=format&fit=crop&q=60",
                Arrays.asList(
                        new WhatsAppTemplateVariableDTO("var1", "Customer Name", "e.g. {{Customer Name}}", "Applicant"),
                        new WhatsAppTemplateVariableDTO("var2", "Loan Offer Title", "e.g. Pre-Approved Personal & Business Loan Offer!", null),
                        new WhatsAppTemplateVariableDTO("var3", "Interest Rate Offer", "e.g. Starting from 8.5% p.a. with Zero Processing Fee", null),
                        new WhatsAppTemplateVariableDTO("var4", "Disposal Time", "e.g. Sanctioned within 30 minutes with minimal docs", null),
                        new WhatsAppTemplateVariableDTO("var5", "Finance Desk Helpline", "e.g. Contact Delta Finance at +91 9881230099", null)
                )
        ));

        // 10. Insurance Renewal Nudge (Text Only)
        templatesList.add(new WhatsAppTemplateDTO(
                10L,
                "Insurance Renewal Nudge (Text Only)",
                "UTILITY",
                "TEXT",
                "Dear {{1}},\n\n{{2}}\n\nðŸ›¡ï¸ Coverage: {{3}}\nðŸŽ Renewal Bonus: {{4}}\n\nRenew today to maintain continuous coverage!",
                null,
                Arrays.asList(
                        new WhatsAppTemplateVariableDTO("var1", "Policyholder Name", "e.g. {{Customer Name}}", "Policyholder"),
                        new WhatsAppTemplateVariableDTO("var2", "Renewal Notice", "e.g. Your Motor / Health Policy is due for renewal!", null),
                        new WhatsAppTemplateVariableDTO("var3", "Sum Insured / Coverage", "e.g. Cashless Coverage across 10,000+ Network Hospitals", null),
                        new WhatsAppTemplateVariableDTO("var4", "No-Claim Bonus Benefit", "e.g. 50% No-Claim Bonus (NCB) Discount Applied", null)
                )
        ));
    }
}
