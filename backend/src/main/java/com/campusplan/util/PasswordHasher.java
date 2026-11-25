package com.campusplan.util;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 비밀번호 해시 생성·검증. 저장 형식: pbkdf2_sha256$반복횟수$salt(hex)$hash(hex)
 * (PBKDF2-HMAC-SHA256, 키 길이 32바이트). 평문 비밀번호는 저장하거나 기록하지 않습니다.
 */
public final class PasswordHasher {

    private static final String PREFIX = "pbkdf2_sha256";
    private static final int ITERATIONS = 600_000;
    private static final int MAX_ITERATIONS = 2_000_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;

    /** 존재하지 않는 계정일 때 같은 계산 비용을 쓰기 위한 더미 해시 */
    private static final String DUMMY_HASH = hash("dummy-password-for-timing");

    private PasswordHasher() {
    }

    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] derived = derive(password, salt, ITERATIONS);
        HexFormat hex = HexFormat.of();
        return PREFIX + "$" + ITERATIONS + "$" + hex.formatHex(salt) + "$" + hex.formatHex(derived);
    }

    /** 저장된 해시와 비밀번호가 일치하면 true. 형식이 잘못된 해시는 false 입니다. */
    public static boolean verify(String stored, String password) {
        try {
            String[] parts = stored.split("\\$");
            if (parts.length != 4 || !PREFIX.equals(parts[0])) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 1 || iterations > MAX_ITERATIONS) {
                return false;
            }
            HexFormat hex = HexFormat.of();
            byte[] expected = hex.parseHex(parts[3]);
            byte[] actual = derive(password, hex.parseHex(parts[2]), iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 계정이 없을 때도 비밀번호 검증과 같은 시간을 쓰도록 더미 해시를 비교합니다. 항상 false. */
    public static boolean verifyDummy(String password) {
        verify(DUMMY_HASH, password);
        return false;
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 를 사용할 수 없습니다.", e);
        } finally {
            spec.clearPassword();
        }
    }
}
