/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.http.ResponseEntity
 *  org.springframework.security.core.Authentication
 *  org.springframework.security.core.context.ReactiveSecurityContextHolder
 *  org.springframework.web.bind.annotation.DeleteMapping
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.PutMapping
 *  org.springframework.web.bind.annotation.RequestBody
 *  org.springframework.web.bind.annotation.RequestHeader
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RequestParam
 *  org.springframework.web.bind.annotation.RestController
 *  reactor.core.publisher.Flux
 *  reactor.core.publisher.Mono
 *  run.halo.app.core.extension.User
 *  run.halo.app.extension.Metadata
 *  run.halo.app.extension.MetadataOperator
 */
package cn.miaohaha.passwordbook.controller;

import cn.miaohaha.passwordbook.extension.PasswordBookNote;
import cn.miaohaha.passwordbook.repository.PasswordBookStore;
import cn.miaohaha.passwordbook.service.CryptoService;
import cn.miaohaha.passwordbook.service.PasswordBookSession;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.MetadataOperator;

@RestController
@RequestMapping(value={"/apis/passwordbook.halo.run/v1alpha1/passwordbook"})
public class PasswordBookController {
    private static final int MIN_PASSWORD_LEN = 8;
    private final CryptoService crypto;
    private final PasswordBookStore store;
    private final PasswordBookSession session;

    public PasswordBookController(CryptoService crypto, PasswordBookStore store, PasswordBookSession session) {
        this.crypto = crypto;
        this.store = store;
        this.session = session;
    }

    private SecretKey requireKey(String token) {
        return this.session.get(token);
    }

    private Mono<String> currentUser() {
        return ReactiveSecurityContextHolder.getContext().map(ctx -> {
            Authentication auth = ctx.getAuthentication();
            if (auth == null || auth.getPrincipal() == null) {
                return "anonymous";
            }
            Object p = auth.getPrincipal();
            if (p instanceof User) {
                return ((User)p).getMetadata().getName();
            }
            return auth.getName();
        });
    }

    private Mono<ResponseEntity<?>> unauthorized() {
        return Mono.just((Object)ResponseEntity.status((int)401).body(Map.of("error", "\u672a\u89e3\u9501\u6216\u4f1a\u8bdd\u5df2\u8fc7\u671f")));
    }

    private Mono<ResponseEntity<?>> forbidden() {
        return Mono.just((Object)ResponseEntity.status((int)403).body(Map.of("error", "\u65e0\u6743\u8bbf\u95ee\u8be5\u8bb0\u5f55")));
    }

    private Mono<ResponseEntity<?>> badRequest(String msg) {
        return Mono.just((Object)ResponseEntity.status((int)400).body(Map.of("error", msg)));
    }

    private Mono<ResponseEntity<?>> validatePassword(String pw) {
        if (pw == null || pw.isEmpty()) {
            return Mono.just((Object)ResponseEntity.status((int)400).body(Map.of("error", "\u5bc6\u7801\u4e0d\u80fd\u4e3a\u7a7a")));
        }
        if (pw.length() < 8) {
            return Mono.just((Object)ResponseEntity.status((int)400).body(Map.of("error", "\u5bc6\u7801\u81f3\u5c11 8 \u4f4d")));
        }
        return Mono.empty();
    }

    private SecretKey authedKey(String token, String user) {
        SecretKey key = this.requireKey(token);
        if (key == null) {
            return null;
        }
        String owner = this.session.userOf(token);
        if (owner == null || !owner.equals(user)) {
            return null;
        }
        return key;
    }

    @GetMapping(value={"/status"})
    public Mono<ResponseEntity<?>> status() {
        return this.currentUser().flatMap(user -> this.store.getMeta("master_salt:" + user).switchIfEmpty(this.store.getMeta("master_salt")).flatMap(s -> Mono.just((Object)ResponseEntity.ok(Map.of("initialized", true)))).switchIfEmpty(Mono.just((Object)ResponseEntity.ok(Map.of("initialized", false)))));
    }

    @PostMapping(value={"/unlock"})
    public Mono<ResponseEntity<?>> unlock(@RequestBody Map<String, String> body) {
        String password = body.get("password");
        if (password == null || password.isEmpty()) {
            return this.badRequest("\u5bc6\u7801\u4e0d\u80fd\u4e3a\u7a7a");
        }
        return this.currentUser().flatMap(user -> {
            String saltKey = "master_salt:" + user;
            String verKey = "verifier:" + user;
            return this.store.getMeta("master_salt").flatMap(legacySaltB64 -> this.migrateLegacy((String)legacySaltB64, password, saltKey, verKey, (String)user)).switchIfEmpty(this.store.getMeta(saltKey).flatMap(saltB64 -> this.verifyWith((String)saltB64, password, verKey, (String)user))).switchIfEmpty(Mono.just((Object)ResponseEntity.ok(Map.of("initialized", false))));
        });
    }

    @PostMapping(value={"/setup"})
    public Mono<ResponseEntity<?>> setup(@RequestBody Map<String, String> body) {
        String password = body.get("password");
        ResponseEntity invalid = (ResponseEntity)this.validatePassword(password).block();
        if (invalid != null) {
            return Mono.just((Object)invalid);
        }
        return this.currentUser().flatMap(user -> {
            String saltKey = "master_salt:" + user;
            String verKey = "verifier:" + user;
            return this.store.getMeta(saltKey).flatMap(existing -> Mono.just((Object)ResponseEntity.status((int)409).body(Map.of("error", "\u5df2\u521d\u59cb\u5316\uff0c\u8bf7\u76f4\u63a5\u89e3\u9501")))).switchIfEmpty(Mono.defer(() -> {
                try {
                    byte[] salt = this.crypto.randomSalt();
                    SecretKey key = this.crypto.deriveKey(password, salt);
                    String ver = this.crypto.encrypt(this.crypto.verifierPlaintext(), key);
                    return this.store.setMeta(saltKey, Base64.getEncoder().encodeToString(salt)).then(this.store.setMeta(verKey, ver)).then(Mono.fromCallable(() -> ResponseEntity.ok(Map.of("token", this.session.create(key, (String)user), "initialized", true))));
                }
                catch (Exception e) {
                    return Mono.just((Object)ResponseEntity.status((int)500).body(Map.of("error", "\u521d\u59cb\u5316\u5931\u8d25")));
                }
            }));
        });
    }

    private Mono<ResponseEntity<?>> verifyWith(String saltB64, String password, String verKey, String user) {
        SecretKey key;
        try {
            key = this.crypto.deriveKey(password, Base64.getDecoder().decode(saltB64));
        }
        catch (Exception e) {
            return Mono.just((Object)ResponseEntity.status((int)500).body(Map.of("error", "\u6d3e\u751f\u5bc6\u94a5\u5931\u8d25")));
        }
        return this.store.getMeta(verKey).flatMap(verB64 -> this.verifyAndToken(key, (String)verB64, user)).switchIfEmpty(this.setupVerifier(key, verKey, user));
    }

    private Mono<ResponseEntity<?>> migrateLegacy(String legacySaltB64, String password, String saltKey, String verKey, String user) {
        SecretKey key;
        try {
            key = this.crypto.deriveKey(password, Base64.getDecoder().decode(legacySaltB64));
        }
        catch (Exception e) {
            return Mono.just((Object)ResponseEntity.status((int)500).body(Map.of("error", "\u6d3e\u751f\u5bc6\u94a5\u5931\u8d25")));
        }
        return this.store.getMeta("verifier").flatMap(legacyVerB64 -> {
            String ver;
            boolean ok;
            try {
                ok = this.crypto.verifierPlaintext().equals(this.crypto.decrypt((String)legacyVerB64, key));
            }
            catch (Exception e) {
                ok = false;
            }
            if (!ok) {
                return Mono.just((Object)ResponseEntity.status((int)401).body(Map.of("error", "\u4e8c\u6b21\u5bc6\u7801\u9519\u8bef")));
            }
            try {
                ver = this.crypto.encrypt(this.crypto.verifierPlaintext(), key);
            }
            catch (Exception e) {
                return Mono.just((Object)ResponseEntity.status((int)500).body(Map.of("error", "\u521d\u59cb\u5316\u5931\u8d25")));
            }
            return this.store.setMeta(saltKey, legacySaltB64).then(this.store.setMeta(verKey, ver)).then(this.tokenResponse(key, user));
        }).switchIfEmpty(Mono.empty());
    }

    private Mono<ResponseEntity<?>> verifyAndToken(SecretKey key, String verB64, String user) {
        try {
            String dec = this.crypto.decrypt(verB64, key);
            if (this.crypto.verifierPlaintext().equals(dec)) {
                return this.tokenResponse(key, user);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return Mono.just((Object)ResponseEntity.status((int)401).body(Map.of("error", "\u4e8c\u6b21\u5bc6\u7801\u9519\u8bef")));
    }

    private Mono<ResponseEntity<?>> setupVerifier(SecretKey key, String verKey, String user) {
        try {
            String ver = this.crypto.encrypt(this.crypto.verifierPlaintext(), key);
            return this.store.setMeta(verKey, ver).then(this.tokenResponse(key, user));
        }
        catch (Exception e) {
            return Mono.just((Object)ResponseEntity.status((int)500).body(Map.of("error", "\u521d\u59cb\u5316\u5931\u8d25")));
        }
    }

    private Mono<ResponseEntity<?>> tokenResponse(SecretKey key, String user) {
        return Mono.fromCallable(() -> ResponseEntity.ok(Map.of("token", this.session.create(key, user))));
    }

    @PostMapping(value={"/change-password"})
    public Mono<ResponseEntity<?>> changePassword(@RequestBody Map<String, String> body) {
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (oldPassword == null || newPassword == null || newPassword.isEmpty()) {
            return this.badRequest("\u5bc6\u7801\u4e0d\u80fd\u4e3a\u7a7a");
        }
        ResponseEntity invalid = (ResponseEntity)this.validatePassword(newPassword).block();
        if (invalid != null) {
            return Mono.just((Object)invalid);
        }
        if (oldPassword.equals(newPassword)) {
            return this.badRequest("\u65b0\u5bc6\u7801\u4e0d\u80fd\u4e0e\u65e7\u5bc6\u7801\u76f8\u540c");
        }
        return this.currentUser().flatMap(user -> {
            String saltKey = "master_salt:" + user;
            String verKey = "verifier:" + user;
            return this.verifyOldKey(oldPassword, saltKey, verKey).switchIfEmpty(this.verifyOldKey(oldPassword, "master_salt", "verifier")).flatMap(oldKey -> {
                try {
                    byte[] newSalt = this.crypto.randomSalt();
                    SecretKey newKey = this.crypto.deriveKey(newPassword, newSalt);
                    String newVer = this.crypto.encrypt(this.crypto.verifierPlaintext(), newKey);
                    String newSaltB64 = Base64.getEncoder().encodeToString(newSalt);
                    SecretKey fNewKey = newKey;
                    return this.reEncryptNotes((SecretKey)oldKey, fNewKey, (String)user, saltKey, verKey, newSaltB64, newVer).then(Mono.fromCallable(() -> {
                        this.session.revokeUser((String)user);
                        return ResponseEntity.ok(Map.of("token", this.session.create(fNewKey, (String)user), "mustChange", false));
                    }));
                }
                catch (Exception e) {
                    return Mono.just((Object)ResponseEntity.status((int)500).body(Map.of("error", "\u52a0\u5bc6\u5931\u8d25")));
                }
            }).switchIfEmpty(Mono.just((Object)ResponseEntity.status((int)401).body(Map.of("error", "\u65e7\u5bc6\u7801\u9519\u8bef"))));
        });
    }

    private Mono<SecretKey> verifyOldKey(String password, String saltKeyName, String verKeyName) {
        return this.store.getMeta(saltKeyName).flatMap(saltB64 -> {
            SecretKey key;
            try {
                key = this.crypto.deriveKey(password, Base64.getDecoder().decode((String)saltB64));
            }
            catch (Exception e) {
                return Mono.empty();
            }
            return this.store.getMeta(verKeyName).flatMap(verB64 -> {
                boolean ok;
                try {
                    ok = this.crypto.verifierPlaintext().equals(this.crypto.decrypt((String)verB64, key));
                }
                catch (Exception e) {
                    ok = false;
                }
                return ok ? Mono.just((Object)key) : Mono.empty();
            });
        });
    }

    private Mono<Void> reEncryptNotes(SecretKey oldKey, SecretKey newKey, String user, String saltKey, String verKey, String newSaltB64, String newVer) {
        return this.store.listNotes().filter(n -> user.equals(n.getSpec().getOwner())).collectList().flatMap(notes -> {
            ArrayList<String> oldTitles = new ArrayList<String>();
            ArrayList<String> oldContents = new ArrayList<String>();
            ArrayList<String> newTitles = new ArrayList<String>();
            ArrayList<String> newContents = new ArrayList<String>();
            for (PasswordBookNote n : notes) {
                try {
                    String t = this.crypto.decrypt(n.getSpec().getTitleData(), oldKey);
                    String c = this.crypto.decrypt(n.getSpec().getContentData(), oldKey);
                    oldTitles.add(n.getSpec().getTitleData());
                    oldContents.add(n.getSpec().getContentData());
                    newTitles.add(this.crypto.encrypt(t, newKey));
                    newContents.add(this.crypto.encrypt(c, newKey));
                }
                catch (Exception e) {
                    return Mono.error((Throwable)new IllegalStateException("\u5b58\u5728\u65e0\u6cd5\u7528\u539f\u5bc6\u7801\u89e3\u5bc6\u7684\u7b14\u8bb0\uff0c\u5df2\u4e2d\u6b62\u6539\u5bc6\uff0c\u672a\u6539\u52a8\u4efb\u4f55\u6570\u636e", e));
                }
            }
            return Flux.range((int)0, (int)notes.size()).concatMap(i -> {
                PasswordBookNote n = (PasswordBookNote)((Object)((Object)((Object)notes.get((int)i))));
                n.getSpec().setTitleData((String)newTitles.get((int)i));
                n.getSpec().setContentData((String)newContents.get((int)i));
                return this.store.updateNote(n).onErrorResume(err -> this.rollback((List<PasswordBookNote>)notes, (List<String>)oldTitles, (List<String>)oldContents).then(Mono.error((Throwable)err)));
            }).then(this.store.setMeta(saltKey, newSaltB64)).then(this.store.setMeta(verKey, newVer)).then(this.store.setMeta("master_salt", newSaltB64)).then(this.store.setMeta("verifier", newVer));
        });
    }

    private Mono<Void> rollback(List<PasswordBookNote> notes, List<String> oldTitles, List<String> oldContents) {
        return Flux.range((int)0, (int)notes.size()).flatMap(i -> {
            PasswordBookNote n = (PasswordBookNote)((Object)((Object)notes.get((int)i)));
            n.getSpec().setTitleData((String)oldTitles.get((int)i));
            n.getSpec().setContentData((String)oldContents.get((int)i));
            return this.store.updateNote(n);
        }).then();
    }

    @GetMapping(value={"/notes"})
    public Mono<ResponseEntity<?>> list(@RequestHeader(value="X-PasswordBook-Token") String token) {
        return this.currentUser().flatMap(user -> {
            SecretKey key = this.authedKey(token, (String)user);
            if (key == null) {
                return this.unauthorized();
            }
            return this.store.listNotes().filter(n -> this.canRead((PasswordBookNote)((Object)((Object)n)), (String)user)).map(n -> {
                Object preview;
                LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("id", n.getMetadata().getName());
                item.put("title", this.tryDecrypt(n.getSpec().getTitleData(), key));
                String raw = this.tryDecrypt(n.getSpec().getContentData(), key);
                String ct = n.getSpec().getContentType();
                Object object = preview = "html".equals(ct) ? this.plainText(raw) : raw;
                if (((String)preview).length() > 120) {
                    preview = ((String)preview).substring(0, 120) + "\u2026";
                }
                item.put("preview", preview);
                item.put("contentType", ct);
                item.put("owner", n.getSpec().getOwner());
                item.put("updatedAt", n.getSpec().getUpdatedAt());
                return item;
            }).collectList().map(ResponseEntity::ok);
        });
    }

    @GetMapping(value={"/notes/detail"})
    public Mono<ResponseEntity<?>> get(@RequestHeader(value="X-PasswordBook-Token") String token, @RequestParam(value="id") String id) {
        return this.currentUser().flatMap(user -> {
            SecretKey key = this.authedKey(token, (String)user);
            if (key == null) {
                return this.unauthorized();
            }
            return this.store.getNote(id).map(n -> {
                if (!this.canRead((PasswordBookNote)((Object)((Object)n)), (String)user)) {
                    return ResponseEntity.status((int)403).body(Map.of("error", "\u65e0\u6743\u8bbf\u95ee\u8be5\u8bb0\u5f55"));
                }
                LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
                body.put("id", n.getMetadata().getName());
                body.put("title", this.tryDecrypt(n.getSpec().getTitleData(), key));
                body.put("content", this.tryDecrypt(n.getSpec().getContentData(), key));
                body.put("contentType", n.getSpec().getContentType());
                body.put("createdAt", n.getSpec().getCreatedAt());
                body.put("updatedAt", n.getSpec().getUpdatedAt());
                return ResponseEntity.ok(body);
            }).switchIfEmpty(Mono.just((Object)ResponseEntity.status((int)404).body(Map.of("error", "\u672a\u627e\u5230"))));
        });
    }

    @PostMapping(value={"/notes"})
    public Mono<ResponseEntity<?>> create(@RequestHeader(value="X-PasswordBook-Token") String token, @RequestBody Map<String, String> body) {
        return this.currentUser().flatMap(user -> {
            PasswordBookNote note;
            String ct;
            SecretKey key = this.authedKey(token, (String)user);
            if (key == null) {
                return this.unauthorized();
            }
            String title = (String)body.get("title");
            String content = (String)body.get("content");
            String contentType = (String)body.get("contentType");
            if (title == null || title.isEmpty()) {
                return this.badRequest("\u6807\u9898\u4e0d\u80fd\u4e3a\u7a7a");
            }
            String string = ct = contentType == null || contentType.isEmpty() ? "text" : contentType;
            if ("anonymous".equals(user)) {
                return this.forbidden();
            }
            long now = System.currentTimeMillis();
            try {
                note = new PasswordBookNote();
                note.setMetadata((MetadataOperator)new Metadata());
                note.getMetadata().setName("pb-" + UUID.randomUUID().toString().replace("-", ""));
                PasswordBookNote.Spec spec = new PasswordBookNote.Spec();
                spec.setTitleData(this.crypto.encrypt(title, key));
                spec.setContentData(this.crypto.encrypt(content == null ? "" : content, key));
                spec.setContentType(ct);
                spec.setOwner((String)user);
                spec.setCreatedAt(now);
                spec.setUpdatedAt(now);
                note.setSpec(spec);
            }
            catch (Exception e) {
                return Mono.just((Object)ResponseEntity.status((int)500).body(Map.of("error", "\u52a0\u5bc6\u5931\u8d25")));
            }
            return this.store.createNote(note).map(saved -> ResponseEntity.ok(Map.of("id", saved.getMetadata().getName())));
        });
    }

    @PutMapping(value={"/notes"})
    public Mono<ResponseEntity<?>> update(@RequestHeader(value="X-PasswordBook-Token") String token, @RequestBody Map<String, String> body) {
        return this.currentUser().flatMap(user -> {
            String ct;
            SecretKey key = this.authedKey(token, (String)user);
            if (key == null) {
                return this.unauthorized();
            }
            String id = (String)body.get("id");
            String title = (String)body.get("title");
            String content = (String)body.get("content");
            String contentType = (String)body.get("contentType");
            if (id == null || id.isEmpty() || title == null || title.isEmpty()) {
                return this.badRequest("\u53c2\u6570\u4e0d\u5b8c\u6574");
            }
            String string = ct = contentType == null || contentType.isEmpty() ? "text" : contentType;
            if ("anonymous".equals(user)) {
                return this.forbidden();
            }
            return this.store.getNote(id).flatMap(n -> {
                if (n.getSpec().getOwner() != null && !user.equals(n.getSpec().getOwner())) {
                    return this.forbidden();
                }
                try {
                    n.getSpec().setTitleData(this.crypto.encrypt(title, key));
                    n.getSpec().setContentData(this.crypto.encrypt(content == null ? "" : content, key));
                    n.getSpec().setContentType(ct);
                    if (n.getSpec().getOwner() == null) {
                        n.getSpec().setOwner((String)user);
                    }
                    n.getSpec().setUpdatedAt(System.currentTimeMillis());
                }
                catch (Exception e) {
                    return Mono.just((Object)ResponseEntity.status((int)500).body(Map.of("error", "\u52a0\u5bc6\u5931\u8d25")));
                }
                return this.store.updateNote((PasswordBookNote)((Object)((Object)n))).map(saved -> ResponseEntity.ok(Map.of("id", id))).switchIfEmpty(Mono.just((Object)ResponseEntity.status((int)404).body(Map.of("error", "\u672a\u627e\u5230"))));
            }).switchIfEmpty(Mono.just((Object)ResponseEntity.status((int)404).body(Map.of("error", "\u672a\u627e\u5230"))));
        });
    }

    @DeleteMapping(value={"/notes"})
    public Mono<ResponseEntity<?>> delete(@RequestHeader(value="X-PasswordBook-Token") String token, @RequestParam(value="id") String id) {
        return this.currentUser().flatMap(user -> {
            SecretKey key = this.authedKey(token, (String)user);
            if (key == null) {
                return this.unauthorized();
            }
            if ("anonymous".equals(user)) {
                return this.forbidden();
            }
            return this.store.getNote(id).flatMap(n -> {
                if (n.getSpec().getOwner() == null || !user.equals(n.getSpec().getOwner())) {
                    return this.forbidden();
                }
                return this.store.deleteNote(id).then(Mono.just((Object)ResponseEntity.ok(Map.of("id", id))));
            }).switchIfEmpty(Mono.just((Object)ResponseEntity.status((int)404).body(Map.of("error", "\u672a\u627e\u5230"))));
        });
    }

    @PostMapping(value={"/lock"})
    public Mono<ResponseEntity<?>> lock(@RequestHeader(value="X-PasswordBook-Token") String token) {
        this.session.remove(token);
        return Mono.just((Object)ResponseEntity.ok(Map.of("ok", true)));
    }

    @PostMapping(value={"/revoke"})
    public Mono<ResponseEntity<?>> revoke(@RequestHeader(value="X-PasswordBook-Token") String token) {
        return this.currentUser().flatMap(user -> {
            this.session.revokeUser((String)user);
            return Mono.just((Object)ResponseEntity.ok(Map.of("ok", true)));
        });
    }

    private boolean canRead(PasswordBookNote n, String user) {
        String owner = n.getSpec().getOwner();
        return user.equals(owner) || owner == null;
    }

    private String tryDecrypt(String payload, SecretKey key) {
        try {
            return this.crypto.decrypt(payload, key);
        }
        catch (Exception e) {
            return "";
        }
    }

    private String plainText(String html) {
        if (html == null) {
            return "";
        }
        String s = html.replaceAll("(?s)<[^>]+>", " ").replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'");
        return s.replaceAll("\\s+", " ").trim();
    }
}

