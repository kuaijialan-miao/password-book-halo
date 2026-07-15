package cn.miaohaha.passwordbook.service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * 加密服务。
 *
 * 性能注意（对应应用市场审核指南 2.4）：
 * - PBKDF2 派生（60 万次迭代）是CPU密集型阻塞操作，调用方必须将其调度到受限的
 *   工作线程（见 {@code PasswordBookConfig#cryptoScheduler}），不得在响应式请求线程执行。
 * - AES-GCM 的 IV 与派生盐仅需唯一性，不要求强熵源；因此复用单个普通
 *   {@link SecureRandom} 实例，避免在每次加密时创建会阻塞的 {@code SecureRandom.getInstanceStrong()}。
 */
@Service
public class CryptoService {
    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_LEN = 128;
    private static final int KEY_LEN = 256;
    private static final int PBKDF2_ITER = 600000;
    private static final String VERIFIER = "VERIFIER-OK";

    // 复用的非阻塞随机源（单实例，避免每次加密创建 getInstanceStrong 的阻塞实例）
    private final SecureRandom random = new SecureRandom();

    public SecretKey deriveKey(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITER, KEY_LEN);
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }

    public String encrypt(String plaintext, SecretKey key) throws Exception {
        byte[] iv = new byte[IV_LEN];
        random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(1, (Key) key, new GCMParameterSpec(TAG_LEN, iv));
        byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(ct);
    }

    public String decrypt(String payload, SecretKey key) throws Exception {
        String[] parts = payload.split(":", 2);
        byte[] iv = Base64.getDecoder().decode(parts[0]);
        byte[] ct = Base64.getDecoder().decode(parts[1]);
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(2, (Key) key, new GCMParameterSpec(TAG_LEN, iv));
        return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
    }

    public byte[] randomSalt() {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return salt;
    }

    public String verifierPlaintext() {
        return VERIFIER;
    }
}
