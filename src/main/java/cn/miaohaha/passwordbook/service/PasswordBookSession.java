package cn.miaohaha.passwordbook.service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * 内存会话管理：每个已解锁用户持有一个 token，绑定其派生密钥与用户名。
 * token 闲置超过 {@link #SESSION_TTL_MS}（默认 30 分钟）自动失效。
 */
@Component
public class PasswordBookSession {
    public static final long SESSION_TTL_MS = Duration.ofMinutes(30L).toMillis();
    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();

    public String create(SecretKey key, String user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        tokens.put(token, new Entry(key, user, now, now));
        return token;
    }

    public SecretKey get(String token) {
        if (token == null) {
            return null;
        }
        Entry e = tokens.get(token);
        if (e == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (e.expired(now)) {
            tokens.remove(token);
            return null;
        }
        tokens.put(token, e.touch());
        return e.key();
    }

    public String userOf(String token) {
        if (token == null) {
            return null;
        }
        Entry e = tokens.get(token);
        if (e == null) {
            return null;
        }
        if (e.expired(System.currentTimeMillis())) {
            tokens.remove(token);
            return null;
        }
        return e.user();
    }

    public void remove(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }

    public void revokeUser(String user) {
        if (user == null) {
            return;
        }
        tokens.entrySet().removeIf(en -> user.equals(en.getValue().user()));
    }

    public void revokeAll() {
        tokens.clear();
    }

    public int size() {
        return tokens.size();
    }

    private record Entry(SecretKey key, String user, long createdAt, long lastAccess) {
        Entry touch() {
            return new Entry(key, user, createdAt, System.currentTimeMillis());
        }

        boolean expired(long now) {
            return now - lastAccess > SESSION_TTL_MS;
        }
    }
}
