package com.adalat.service.serviceImpl;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


@Service
public class TokenBlacklistService {

    private final Set<String> blacklistedTokenIds = ConcurrentHashMap.newKeySet();

    public void blacklist(String jti) {
        blacklistedTokenIds.add(jti);
    }

    public boolean isBlacklisted(String jti) {
        return blacklistedTokenIds.contains(jti);
    }
}

