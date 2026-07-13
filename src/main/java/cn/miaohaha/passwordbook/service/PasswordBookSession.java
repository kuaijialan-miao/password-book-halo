/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Component
 */
package cn.miaohaha.passwordbook.service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class PasswordBookSession {
    public static final long SESSION_TTL_MS = Duration.ofMinutes(30L).toMillis();
    private final Map<String, Entry> tokens = new ConcurrentHashMap<String, Entry>();

    public String create(SecretKey key, String user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        this.tokens.put(token, new Entry(key, user, now, now));
        return token;
    }

    public SecretKey get(String token) {
        if (token == null) {
            return null;
        }
        Entry e = this.tokens.get(token);
        if (e == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (e.expired(now)) {
            this.tokens.remove(token);
            return null;
        }
        this.tokens.put(token, e.touch());
        return e.key();
    }

    public String userOf(String token) {
        if (token == null) {
            return null;
        }
        Entry e = this.tokens.get(token);
        if (e == null) {
            return null;
        }
        if (e.expired(System.currentTimeMillis())) {
            this.tokens.remove(token);
            return null;
        }
        return e.user();
    }

    public void remove(String token) {
        if (token != null) {
            this.tokens.remove(token);
        }
    }

    public void revokeUser(String user) {
        if (user == null) {
            return;
        }
        this.tokens.entrySet().removeIf(en -> user.equals(((Entry)en.getValue()).user()));
    }

    public void revokeAll() {
        this.tokens.clear();
    }

    public int size() {
        return this.tokens.size();
    }

    private record Entry(SecretKey key, String user, long createdAt, long lastAccess) {
        Entry touch() {
            return new Entry(this.key, this.user, this.createdAt, System.currentTimeMillis());
        }

        boolean expired(long now) {
            return now - this.lastAccess > SESSION_TTL_MS;
        }
    }
}

