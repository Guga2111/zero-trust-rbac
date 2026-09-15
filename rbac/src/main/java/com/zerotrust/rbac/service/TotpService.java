package com.zerotrust.rbac.service;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TotpService {

    private static final int SECRET_LENGTH = 20;
    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final ConcurrentHashMap<String, byte[]> secrets = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final TimeBasedOneTimePasswordGenerator totp;

    public TotpService() throws Exception {
        this.totp = new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30), 6);
    }

    public String enrollUser(String username) {
        byte[] secret = new byte[SECRET_LENGTH];
        secureRandom.nextBytes(secret);
        secrets.put(username, secret);
        String base32Secret = encodeBase32(secret);
        System.out.println("[AUDIT] TOTP_ENROLLED | user=" + username);
        return "otpauth://totp/ZeroTrust:" + username + "?secret=" + base32Secret + "&issuer=ZeroTrust";
    }

    public boolean validateCode(String username, String totpCode) {
        byte[] secret = secrets.get(username);
        if (secret == null) {
            return false;
        }

        try {
            SecretKeySpec key = new SecretKeySpec(secret, totp.getAlgorithm());
            Instant now = Instant.now();

            // Check current window and ±1 window (±30s tolerance)
            for (int i = -1; i <= 1; i++) {
                Instant time = now.plus(Duration.ofSeconds(30L * i));
                String generated = String.format("%06d", totp.generateOneTimePassword(key, time));
                if (generated.equals(totpCode)) {
                    return true;
                }
            }
        } catch (InvalidKeyException e) {
            return false;
        }

        return false;
    }

    public boolean isEnrolled(String username) {
        return secrets.containsKey(username);
    }

    private String encodeBase32(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;

        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                result.append(BASE32_CHARS.charAt((buffer >> bitsLeft) & 0x1F));
            }
        }

        if (bitsLeft > 0) {
            result.append(BASE32_CHARS.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }

        return result.toString();
    }
}
