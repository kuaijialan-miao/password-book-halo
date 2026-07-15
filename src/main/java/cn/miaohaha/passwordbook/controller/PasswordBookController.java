package cn.miaohaha.passwordbook.controller;

import cn.miaohaha.passwordbook.extension.PasswordBookCategory;
import cn.miaohaha.passwordbook.extension.PasswordBookNote;
import cn.miaohaha.passwordbook.repository.PasswordBookStore;
import cn.miaohaha.passwordbook.service.CryptoService;
import cn.miaohaha.passwordbook.service.PasswordBookSession;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
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
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.User;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.MetadataOperator;

@RestController
@RequestMapping(value = {"/apis/passwordbook.halo.run/v1alpha1/passwordbook"})
public class PasswordBookController {
    private static final int MIN_PASSWORD_LEN = 8;
    private static final String DEFAULT_PASSWORD = "12345678";
    private static final String GLOBAL_SALT_KEY = "master_salt";
    private static final String GLOBAL_VER_KEY = "verifier";

    private final CryptoService crypto;
    private final PasswordBookStore store;
    private final PasswordBookSession session;
    // 受限工作线程池：PBKDF2(60万次) 与 AES-GCM 为阻塞调用，必须离开响应式事件线程执行。
    // 直接在插件内实例化（插件 Spring 上下文不处理 @Configuration @Bean，故不走 DI）。
    private final Scheduler cryptoScheduler = Schedulers.newBoundedElastic(
            Math.max(2, Runtime.getRuntime().availableProcessors() * 2), 64, "pb-crypto", 60);

    public PasswordBookController(CryptoService crypto, PasswordBookStore store,
                                  PasswordBookSession session) {
        this.crypto = crypto;
        this.store = store;
        this.session = session;
    }

    // 将阻塞的密码学操作调度到受限工作线程，离开响应式事件线程
    private <T> Mono<T> crypto(Callable<T> task) {
        return Mono.fromCallable(task).subscribeOn(cryptoScheduler);
    }

    private static Mono<ResponseEntity> wrap(ResponseEntity resp) {
        return Mono.just(resp);
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
                return ((User) p).getMetadata().getName();
            }
            return auth.getName();
        });
    }

    private Mono<ResponseEntity> unauthorized() {
        return Mono.just((ResponseEntity) ResponseEntity.status(401).body(Map.of("error", "未解锁或会话已过期")));
    }

    private Mono<ResponseEntity> forbidden() {
        return Mono.just((ResponseEntity) ResponseEntity.status(403).body(Map.of("error", "无权访问该记录")));
    }

    private Mono<ResponseEntity> badRequest(String msg) {
        return Mono.just((ResponseEntity) ResponseEntity.status(400).body(Map.of("error", msg)));
    }

    private Mono<ResponseEntity> validatePassword(String pw) {
        if (pw == null || pw.isEmpty()) {
            return Mono.just((ResponseEntity) ResponseEntity.status(400).body(Map.of("error", "密码不能为空")));
        }
        if (pw.length() < 8) {
            return Mono.just((ResponseEntity) ResponseEntity.status(400).body(Map.of("error", "密码至少 8 位")));
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

    @GetMapping(value = {"/status"})
    public Mono<ResponseEntity> status() {
        return this.currentUser().flatMap(user -> {
            String saltKey = "master_salt:" + user;
            String mcKey = "must_change:" + user;
            return this.store.getMeta(saltKey).flatMap(s -> this.store.getMeta(mcKey)
                .map(v -> (ResponseEntity) ResponseEntity.ok(Map.of("initialized", true, "mustChange", true)))
                .switchIfEmpty(Mono.defer(() -> wrap((ResponseEntity) ResponseEntity.ok(Map.of("initialized", true, "mustChange", false)))))
            ).switchIfEmpty(Mono.defer(() -> wrap((ResponseEntity) ResponseEntity.ok(Map.of("initialized", false, "mustChange", false)))));
        });
    }

    @PostMapping(value = {"/unlock"})
    public Mono<ResponseEntity> unlock(@RequestBody Map<String, String> body) {
        String password = body.get("password");
        if (password == null || password.isEmpty()) {
            return this.badRequest("密码不能为空");
        }
        return this.currentUser().flatMap(user -> {
            String saltKey = "master_salt:" + user;
            String verKey = "verifier:" + user;
            return this.store.getMeta(saltKey)
                .flatMap(saltB64 -> this.verifyWith((String) saltB64, password, verKey, (String) user))
                .switchIfEmpty(Mono.defer(() -> {
                    if (!DEFAULT_PASSWORD.equals(password)) {
                        return wrap((ResponseEntity) ResponseEntity.status(401)
                            .body(Map.of("error", "新账号请使用默认密码 12345678 解锁")));
                    }
                    return this.bootstrapDefault((String) user, saltKey, verKey);
                }));
        });
    }

    private Mono<ResponseEntity> bootstrapDefault(String user, String saltKey, String verKey) {
        return this.crypto(() -> {
            byte[] salt = this.crypto.randomSalt();
            SecretKey key = this.crypto.deriveKey(DEFAULT_PASSWORD, salt);
            String ver = this.crypto.encrypt(this.crypto.verifierPlaintext(), key);
            String token = this.session.create(key, user);
            // 写入当前用户的独立密钥（不写全局密钥）
            this.store.setMeta(saltKey, Base64.getEncoder().encodeToString(salt)).block();
            this.store.setMeta(verKey, ver).block();
            this.store.setMeta("must_change:" + user, "true").block();
            return (ResponseEntity) ResponseEntity.ok(Map.of("token", token, "initialized", true, "mustChange", true));
        });
    }

    @PostMapping(value = {"/setup"})
    public Mono<ResponseEntity> setup(@RequestBody Map<String, String> body) {
        String password = body.get("password");
        return this.validatePassword(password).flatMap(invalid -> Mono.just(invalid))
            .switchIfEmpty(this.currentUser().flatMap(user -> {
                String saltKey = "master_salt:" + user;
                String verKey = "verifier:" + user;
                return this.store.getMeta(saltKey)
                    .flatMap(existing -> Mono.just((ResponseEntity) ResponseEntity.status(409)
                        .body(Map.of("error", "已初始化，请直接解锁"))))
                    .switchIfEmpty(Mono.defer(() -> this.crypto(() -> {
                        byte[] salt = this.crypto.randomSalt();
                        SecretKey key = this.crypto.deriveKey(password, salt);
                        String ver = this.crypto.encrypt(this.crypto.verifierPlaintext(), key);
                        String token = this.session.create(key, user);
                        this.store.setMeta(saltKey, Base64.getEncoder().encodeToString(salt)).block();
                        this.store.setMeta(verKey, ver).block();
                        return (ResponseEntity) ResponseEntity.ok(Map.of("token", token, "initialized", true));
                    })));
            }));
    }

    private Mono<ResponseEntity> verifyWith(String saltB64, String password, String verKey, String user) {
        return this.crypto(() -> {
            SecretKey key = this.crypto.deriveKey(password, Base64.getDecoder().decode(saltB64));
            return key;
        }).flatMap(key -> this.store.getMeta(verKey)
            .flatMap(verB64 -> this.crypto(() -> {
                String dec = this.crypto.decrypt((String) verB64, key);
                if (!this.crypto.verifierPlaintext().equals(dec)) {
                    throw new SecurityException("bad password");
                }
                return (ResponseEntity) ResponseEntity.ok(Map.of("token", this.session.create(key, user), "initialized", true));
            }).onErrorResume(ex -> Mono.just((ResponseEntity) ResponseEntity.status(401)
                .body(Map.of("error", "密码错误")))))
            .switchIfEmpty(Mono.defer(() -> this.setupVerifier(key, verKey, user)))
        ).onErrorResume(ex -> Mono.just((ResponseEntity) ResponseEntity.status(401)
            .body(Map.of("error", "密码错误"))));
    }

    private Mono<ResponseEntity> setupVerifier(SecretKey key, String verKey, String user) {
        return this.crypto(() -> {
            String ver = this.crypto.encrypt(this.crypto.verifierPlaintext(), key);
            this.store.setMeta(verKey, ver).block();
            return (ResponseEntity) ResponseEntity.ok(Map.of("token", this.session.create(key, user), "initialized", true));
        });
    }

    /**
     * 显式、一次性、受控的遗留数据迁移（对应审核指南 4.3.2）。
     *
     * 仅当存在遗留的全局 master_salt/verifier（旧版单密钥模型）时可用；
     * 使用遗留密码派生全局密钥，验证通过后把 owner 为空的遗留笔记重加密到当前用户，
     * 写入当前用户独立密钥并标记 legacy_migrated:<user>，保证幂等、不可重复执行。
     */
    @PostMapping(value = {"/migrate"})
    public Mono<ResponseEntity> migrate(@RequestHeader(value = "X-PasswordBook-Token") String token,
                                        @RequestBody Map<String, String> body) {
        String legacyPassword = body.get("password");
        if (legacyPassword == null || legacyPassword.isEmpty()) {
            return this.badRequest("需要提供遗留主密码");
        }
        return this.currentUser().flatMap(user -> {
            String saltKey = "master_salt:" + user;
            String verKey = "verifier:" + user;
            String migratedKey = "legacy_migrated:" + user;
            return this.store.getMeta(migratedKey)
                .flatMap(v -> Mono.just((ResponseEntity) ResponseEntity.status(409)
                    .body(Map.of("error", "当前用户已完成迁移，无需重复"))))
                .switchIfEmpty(Mono.defer(() -> this.store.getMeta(GLOBAL_SALT_KEY)
                    .zipWith(this.store.getMeta(GLOBAL_VER_KEY))
                    .flatMap(tuple -> this.crypto(() -> {
                        SecretKey gkey = this.crypto.deriveKey(legacyPassword,
                            Base64.getDecoder().decode((String) tuple.getT1()));
                        String dec = this.crypto.decrypt((String) tuple.getT2(), gkey);
                        if (!this.crypto.verifierPlaintext().equals(dec)) {
                            throw new SecurityException("legacy password wrong");
                        }
                        return gkey;
                    }))
                    .flatMap(gkey -> {
                        byte[] newSalt;
                        SecretKey newKey;
                        String newVer;
                        String newSaltB64;
                        try {
                            newSalt = this.crypto.randomSalt();
                            newKey = this.crypto.deriveKey(legacyPassword, newSalt);
                            newVer = this.crypto.encrypt(this.crypto.verifierPlaintext(), newKey);
                            newSaltB64 = Base64.getEncoder().encodeToString(newSalt);
                        } catch (Exception e) {
                            return Mono.error(e);
                        }
                        return this.reEncryptOwnerless(gkey, newKey, (String) user)
                            .then(this.store.setMeta(saltKey, newSaltB64))
                            .then(this.store.setMeta(verKey, newVer))
                            .then(this.store.setMeta(migratedKey, "true"))
                            .then(Mono.defer(() -> {
                                this.session.revokeUser((String) user);
                                return wrap((ResponseEntity) ResponseEntity.ok(Map.of(
                                    "token", this.session.create(newKey, (String) user),
                                    "migrated", true)));
                            }));
                    })
                    .switchIfEmpty(Mono.just((ResponseEntity) ResponseEntity.status(401)
                        .body(Map.of("error", "无遗留数据或遗留密码错误"))))
                    )
                )
            ;
        });
    }

    @PostMapping(value = {"/change-password"})
    public Mono<ResponseEntity> changePassword(@RequestBody Map<String, String> body) {
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (oldPassword == null || newPassword == null || newPassword.isEmpty()) {
            return this.badRequest("密码不能为空");
        }
        return this.validatePassword(newPassword).flatMap(invalid -> Mono.just(invalid))
            .switchIfEmpty(this.currentUser().flatMap(user -> {
                String saltKey = "master_salt:" + user;
                String verKey = "verifier:" + user;
                // 仅校验当前用户的独立密钥，不再回退到全局密钥（修复多用户串号）
                return this.verifyOldKey(oldPassword, saltKey, verKey)
                    .flatMap(oldKey -> this.crypto(() -> {
                        byte[] newSalt = this.crypto.randomSalt();
                        SecretKey newKey = this.crypto.deriveKey(newPassword, newSalt);
                        String newVer = this.crypto.encrypt(this.crypto.verifierPlaintext(), newKey);
                        return new RekeyCtx(newSalt, newKey, newVer);
                    }).flatMap(ctx -> this.reEncryptNotes(oldKey, ctx.newKey, (String) user, saltKey, verKey,
                            Base64.getEncoder().encodeToString(ctx.newSalt), ctx.newVer)
                        .then(this.store.deleteMeta("must_change:" + (String) user))
                        .then(Mono.defer(() -> {
                            this.session.revokeUser((String) user);
                            return wrap((ResponseEntity) ResponseEntity.ok(Map.of(
                                "token", this.session.create(ctx.newKey, (String) user), "mustChange", false)));
                        }))))
                    .switchIfEmpty(Mono.just((ResponseEntity) ResponseEntity.status(401)
                        .body(Map.of("error", "旧密码错误"))));
            }));
    }

    private static final class RekeyCtx {
        final byte[] newSalt;
        final SecretKey newKey;
        final String newVer;
        RekeyCtx(byte[] newSalt, SecretKey newKey, String newVer) {
            this.newSalt = newSalt;
            this.newKey = newKey;
            this.newVer = newVer;
        }
    }

    private Mono<SecretKey> verifyOldKey(String password, String saltKeyName, String verKeyName) {
        return this.store.getMeta(saltKeyName)
            .zipWith(this.store.getMeta(verKeyName))
            .flatMap(tuple -> this.crypto(() -> {
                SecretKey key = this.crypto.deriveKey(password,
                    Base64.getDecoder().decode((String) tuple.getT1()));
                String dec = this.crypto.decrypt((String) tuple.getT2(), key);
                if (!this.crypto.verifierPlaintext().equals(dec)) {
                    throw new SecurityException("bad password");
                }
                return key;
            }));
    }

    private Mono<Void> reEncryptNotes(SecretKey oldKey, SecretKey newKey, String user,
                                      String saltKey, String verKey, String newSaltB64, String newVer) {
        return this.store.listNotes()
            .filter(n -> user.equals(n.getSpec().getOwner()))
            .collectList()
            .flatMap(notes -> this.crypto(() -> {
                // 先在内存中完成全部解密/加密；任一失败抛出异常，不写入任何数据
                ArrayList<String> oldTitles = new ArrayList<String>();
                ArrayList<String> oldContents = new ArrayList<String>();
                ArrayList<String> newTitles = new ArrayList<String>();
                ArrayList<String> newContents = new ArrayList<String>();
                for (PasswordBookNote n : notes) {
                    String t = this.crypto.decrypt(n.getSpec().getTitleData(), oldKey);
                    String c = this.crypto.decrypt(n.getSpec().getContentData(), oldKey);
                    oldTitles.add(n.getSpec().getTitleData());
                    oldContents.add(n.getSpec().getContentData());
                    newTitles.add(this.crypto.encrypt(t, newKey));
                    newContents.add(this.crypto.encrypt(c, newKey));
                }
                for (int i = 0; i < notes.size(); i++) {
                    notes.get(i).getSpec().setTitleData(newTitles.get(i));
                    notes.get(i).getSpec().setContentData(newContents.get(i));
                }
                return new Rollbackable(notes, oldTitles, oldContents);
            }))
            .flatMap(rb -> Flux.fromIterable(rb.notes)
                .flatMap(n -> this.store.updateNote(n), 4) // 受限并发，避免瞬时打满存储
                .onErrorResume(err -> this.rollback(rb).then(Mono.error(err)))
                .then())
            .then(this.store.setMeta(saltKey, newSaltB64))
            .then(this.store.setMeta(verKey, newVer));
    }

    private Mono<Void> reEncryptOwnerless(SecretKey oldKey, SecretKey newKey, String user) {
        return this.store.listNotes()
            .filter(n -> n.getSpec().getOwner() == null)
            .collectList()
            .flatMap(notes -> this.crypto(() -> {
                for (PasswordBookNote n : notes) {
                    String t = this.crypto.decrypt(n.getSpec().getTitleData(), oldKey);
                    String c = this.crypto.decrypt(n.getSpec().getContentData(), oldKey);
                    n.getSpec().setTitleData(this.crypto.encrypt(t, newKey));
                    n.getSpec().setContentData(this.crypto.encrypt(c, newKey));
                    n.getSpec().setOwner(user);
                }
                return notes;
            }))
            .flatMap(notes -> Flux.fromIterable(notes)
                .flatMap(n -> this.store.updateNote(n), 4)
                .then());
    }

    private Mono<Void> rollback(Rollbackable rb) {
        return Flux.fromIterable(rb.notes)
            .flatMap(n -> {
                int i = rb.notes.indexOf(n);
                n.getSpec().setTitleData(rb.oldTitles.get(i));
                n.getSpec().setContentData(rb.oldContents.get(i));
                return this.store.updateNote(n);
            }).then();
    }

    private static final class Rollbackable {
        final List<PasswordBookNote> notes;
        final List<String> oldTitles;
        final List<String> oldContents;
        Rollbackable(List<PasswordBookNote> notes, List<String> oldTitles, List<String> oldContents) {
            this.notes = notes;
            this.oldTitles = oldTitles;
            this.oldContents = oldContents;
        }
    }

    @GetMapping(value = {"/notes"})
    public Mono<ResponseEntity> list(@RequestHeader(value = "X-PasswordBook-Token") String token,
                                     @RequestParam(value = "category", required = false) String category) {
        return this.currentUser().flatMap(user -> {
            SecretKey key = this.authedKey(token, (String) user);
            if (key == null) {
                return this.unauthorized();
            }
            return this.store.listNotes()
                .filter(n -> this.canRead(n, (String) user))
                .filter(n -> category == null || category.isEmpty() || category.equals(n.getSpec().getCategory()))
                .flatMap(n -> this.crypto(() -> this.buildListItem(n, key)))
                .collectList()
                .map(list -> (ResponseEntity) ResponseEntity.ok(list));
        });
    }

    private LinkedHashMap<String, Object> buildListItem(PasswordBookNote n, SecretKey key) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("id", n.getMetadata().getName());
        String title;
        try {
            title = this.crypto.decrypt(n.getSpec().getTitleData(), key);
        } catch (Exception e) {
            title = n.getSpec().getTitleData();
        }
        item.put("title", title);
        item.put("preview", "内容隐藏，点击查看");
        item.put("contentType", n.getSpec().getContentType());
        item.put("category", n.getSpec().getCategory());
        item.put("owner", n.getSpec().getOwner());
        item.put("updatedAt", n.getSpec().getUpdatedAt());
        return item;
    }

    @GetMapping(value = {"/notes/detail"})
    public Mono<ResponseEntity> get(@RequestHeader(value = "X-PasswordBook-Token") String token,
                                    @RequestParam(value = "id") String id) {
        return this.currentUser().flatMap(user -> {
            SecretKey key = this.authedKey(token, (String) user);
            if (key == null) {
                return this.unauthorized();
            }
            return this.store.getNote(id).flatMap(n -> {
                if (!this.canRead(n, (String) user)) {
                    return Mono.just((ResponseEntity) ResponseEntity.status(403)
                        .body(Map.of("error", "无权访问该记录")));
                }
                return this.crypto(() -> {
                    LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
                    body.put("id", n.getMetadata().getName());
                    body.put("title", this.crypto.decrypt(n.getSpec().getTitleData(), key));
                    body.put("content", this.crypto.decrypt(n.getSpec().getContentData(), key));
                    body.put("contentType", n.getSpec().getContentType());
                    body.put("category", n.getSpec().getCategory());
                    body.put("createdAt", n.getSpec().getCreatedAt());
                    body.put("updatedAt", n.getSpec().getUpdatedAt());
                    return (ResponseEntity) ResponseEntity.ok(body);
                });
            }).switchIfEmpty(Mono.defer(() -> wrap((ResponseEntity) ResponseEntity.status(404)
                .body(Map.of("error", "未找到")))));
        });
    }

    @PostMapping(value = {"/notes"})
    public Mono<ResponseEntity> create(@RequestHeader(value = "X-PasswordBook-Token") String token,
                                       @RequestBody Map<String, String> body) {
        return this.currentUser().flatMap(user -> {
            SecretKey key = this.authedKey(token, (String) user);
            if (key == null) {
                return this.unauthorized();
            }
            String title = (String) body.get("title");
            String content = (String) body.get("content");
            String contentType = (String) body.get("contentType");
            if (title == null || title.isEmpty()) {
                return this.badRequest("标题不能为空");
            }
            String ct = contentType == null || contentType.isEmpty() ? "text" : contentType;
            if ("anonymous".equals(user)) {
                return this.forbidden();
            }
            final String fCt = ct;
            return this.crypto(() -> {
                long now = System.currentTimeMillis();
                PasswordBookNote note = new PasswordBookNote();
                note.setMetadata((MetadataOperator) new Metadata());
                note.getMetadata().setName("pb-" + UUID.randomUUID().toString().replace("-", ""));
                PasswordBookNote.Spec spec = new PasswordBookNote.Spec();
                spec.setTitleData(this.crypto.encrypt(title, key));
                spec.setContentData(this.crypto.encrypt(content == null ? "" : content, key));
                spec.setContentType(fCt);
                spec.setOwner((String) user);
                spec.setCategory((String) body.get("category"));
                spec.setCreatedAt(now);
                spec.setUpdatedAt(now);
                note.setSpec(spec);
                return note;
            }).flatMap(note -> this.store.createNote(note)
                .map(saved -> (ResponseEntity) ResponseEntity.ok(Map.of("id", saved.getMetadata().getName()))));
        });
    }

    @PutMapping(value = {"/notes"})
    public Mono<ResponseEntity> update(@RequestHeader(value = "X-PasswordBook-Token") String token,
                                       @RequestBody Map<String, String> body) {
        return this.currentUser().flatMap(user -> {
            SecretKey key = this.authedKey(token, (String) user);
            if (key == null) {
                return this.unauthorized();
            }
            String id = (String) body.get("id");
            String title = (String) body.get("title");
            String content = (String) body.get("content");
            String contentType = (String) body.get("contentType");
            if (id == null || id.isEmpty() || title == null || title.isEmpty()) {
                return this.badRequest("参数不完整");
            }
            String ct = contentType == null || contentType.isEmpty() ? "text" : contentType;
            if ("anonymous".equals(user)) {
                return this.forbidden();
            }
            final String fCt = ct;
            return this.store.getNote(id).flatMap(n -> {
                // 严格归属校验：owner 为空或未匹配当前用户均拒绝（不允许通过普通更新认领遗留记录）
                if (n.getSpec().getOwner() == null || !user.equals(n.getSpec().getOwner())) {
                    return this.forbidden();
                }
                return this.crypto(() -> {
                    n.getSpec().setTitleData(this.crypto.encrypt(title, key));
                    n.getSpec().setContentData(this.crypto.encrypt(content == null ? "" : content, key));
                    n.getSpec().setContentType(fCt);
                    if (body.get("category") != null) {
                        n.getSpec().setCategory((String) body.get("category"));
                    }
                    n.getSpec().setUpdatedAt(System.currentTimeMillis());
                    return n;
                }).flatMap(note -> this.store.updateNote(note)
                    .map(saved -> (ResponseEntity) ResponseEntity.ok(Map.of("id", id)))
                    .switchIfEmpty(Mono.defer(() -> wrap((ResponseEntity) ResponseEntity.status(404)
                        .body(Map.of("error", "未找到"))))));
            }).switchIfEmpty(Mono.defer(() -> wrap((ResponseEntity) ResponseEntity.status(404)
                .body(Map.of("error", "未找到")))));
        });
    }

    @DeleteMapping(value = {"/notes"})
    public Mono<ResponseEntity> delete(@RequestHeader(value = "X-PasswordBook-Token") String token,
                                       @RequestParam(value = "id") String id) {
        return this.currentUser().flatMap(user -> {
            SecretKey key = this.authedKey(token, (String) user);
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
                return this.store.deleteNote(id).then(wrap((ResponseEntity) ResponseEntity.ok(Map.of("id", id))));
            }).switchIfEmpty(Mono.defer(() -> wrap((ResponseEntity) ResponseEntity.status(404)
                .body(Map.of("error", "未找到")))));
        });
    }

    @PostMapping(value = {"/lock"})
    public Mono<ResponseEntity> lock(@RequestHeader(value = "X-PasswordBook-Token") String token) {
        if (this.session.userOf(token) == null) {
            return Mono.just((ResponseEntity) ResponseEntity.status(401)
                .body(Map.of("error", "未解锁或会话已过期")));
        }
        this.session.remove(token);
        return Mono.just((ResponseEntity) ResponseEntity.ok(Map.of("ok", true)));
    }

    @PostMapping(value = {"/revoke"})
    public Mono<ResponseEntity> revoke(@RequestHeader(value = "X-PasswordBook-Token") String token) {
        return this.currentUser().flatMap(user -> {
            this.session.revokeUser((String) user);
            return Mono.just((ResponseEntity) ResponseEntity.ok(Map.of("ok", true)));
        });
    }

    // ===== 分类管理 =====
    // GET /categories：返回合并列表（managed 实体 + virtual 来自笔记的分类名）
    @GetMapping(value = {"/categories"})
    public Mono<ResponseEntity> listCategories(@RequestHeader(value = "X-PasswordBook-Token") String token) {
        return this.currentUser().flatMap(user -> {
            SecretKey key = this.authedKey(token, (String) user);
            if (key == null) {
                return this.unauthorized();
            }
            return this.store.listCategories((String) user).collectList()
                .flatMap(managed -> this.store.listNoteCategories((String) user)
                    .map(virtuals -> (ResponseEntity) ResponseEntity.ok(this.mergeCategories(managed, virtuals))));
        });
    }

    // POST /categories：新增分类（同名已存在则直接返回已有）
    @PostMapping(value = {"/categories"})
    public Mono<ResponseEntity> createCategory(@RequestHeader(value = "X-PasswordBook-Token") String token,
                                               @RequestBody Map<String, String> body) {
        return this.currentUser().flatMap(user -> {
            SecretKey key = this.authedKey(token, (String) user);
            if (key == null) {
                return this.unauthorized();
            }
            String rawName = body.get("name");
            if (rawName == null || rawName.trim().isEmpty()) {
                return this.badRequest("分类名不能为空");
            }
            final String name = rawName.trim();
            return this.store.listCategories((String) user).collectList().flatMap(existing -> {
                for (PasswordBookCategory c : existing) {
                    if (name.equals(c.getSpec().getName())) {
                        return wrap((ResponseEntity) ResponseEntity.ok(this.catToMap(c, true)));
                    }
                }
                int maxOrder = existing.stream()
                        .mapToInt(c -> c.getSpec().getOrder() == null ? 0 : c.getSpec().getOrder()).max().orElse(0);
                PasswordBookCategory cat = this.newCategory((String) user, name, maxOrder + 1);
                return this.store.createCategory(cat)
                        .map(saved -> (ResponseEntity) ResponseEntity.ok(this.catToMap(saved, true)));
            });
        });
    }

    // PUT /categories：改名 + 回写关联笔记（id 为空表示 virtual，先建实体再回写）
    @PutMapping(value = {"/categories"})
    public Mono<ResponseEntity> renameCategory(@RequestHeader(value = "X-PasswordBook-Token") String token,
                                               @RequestBody Map<String, String> body) {
        return this.currentUser().flatMap(user -> {
            SecretKey key = this.authedKey(token, (String) user);
            if (key == null) {
                return this.unauthorized();
            }
            String rawName = body.get("name");
            if (rawName == null || rawName.trim().isEmpty()) {
                return this.badRequest("分类名不能为空");
            }
            final String newName = rawName.trim();
            String id = body.get("id");
            String oldName = body.get("oldName");
            if (id == null || id.isEmpty()) {
                // virtual：oldName 为待管理的分类名
                if (oldName == null || oldName.isEmpty()) {
                    return this.badRequest("缺少原分类名");
                }
                return this.store.listCategories((String) user).collectList().flatMap(existing -> {
                    for (PasswordBookCategory c : existing) {
                        if (newName.equals(c.getSpec().getName())) {
                            // 目标已存在 managed：直接把旧名笔记迁移过去
                            return this.store.reassignNotesCategory((String) user, oldName, newName)
                                    .then(wrap((ResponseEntity) ResponseEntity.ok(this.catToMap(c, true))));
                        }
                    }
                    int maxOrder = existing.stream()
                            .mapToInt(c -> c.getSpec().getOrder() == null ? 0 : c.getSpec().getOrder()).max().orElse(0);
                    PasswordBookCategory cat = this.newCategory((String) user, newName, maxOrder + 1);
                    return this.store.createCategory(cat)
                            .flatMap(saved -> this.store.reassignNotesCategory((String) user, oldName, newName)
                                    .then(Mono.just((ResponseEntity) ResponseEntity.ok(this.catToMap(saved, true)))));
                });
            }
            return this.store.getCategory(id).flatMap(c -> {
                if (c.getSpec().getOwner() == null || !user.equals(c.getSpec().getOwner())) {
                    return this.forbidden();
                }
                String old = c.getSpec().getName();
                c.getSpec().setName(newName);
                return this.store.updateCategory(c).flatMap(saved ->
                        this.store.reassignNotesCategory((String) user, old, newName)
                                .then(Mono.just((ResponseEntity) ResponseEntity.ok(this.catToMap(saved, true)))));
            }).switchIfEmpty(Mono.defer(() -> wrap((ResponseEntity) ResponseEntity.status(404)
                    .body(Map.of("error", "未找到")))));
        });
    }

    // DELETE /categories：删分类 + 关联笔记归未分类（id 为空则由 name 处理 virtual）
    @DeleteMapping(value = {"/categories"})
    public Mono<ResponseEntity> deleteCategory(@RequestHeader(value = "X-PasswordBook-Token") String token,
                                               @RequestParam(value = "id", required = false) String id,
                                               @RequestParam(value = "name", required = false) String name) {
        return this.currentUser().flatMap(user -> {
            SecretKey key = this.authedKey(token, (String) user);
            if (key == null) {
                return this.unauthorized();
            }
            if (id != null && !id.isEmpty()) {
                return this.store.getCategory(id).flatMap(c -> {
                    if (c.getSpec().getOwner() == null || !user.equals(c.getSpec().getOwner())) {
                        return this.forbidden();
                    }
                    String catName = c.getSpec().getName();
                    return this.store.deleteCategory(id)
                            .then(this.store.clearNotesCategory((String) user, catName))
                            .then(wrap((ResponseEntity) ResponseEntity.ok(Map.of("id", id))));
                }).switchIfEmpty(Mono.defer(() -> wrap((ResponseEntity) ResponseEntity.status(404)
                        .body(Map.of("error", "未找到")))));
            }
            if (name != null && !name.isEmpty()) {
                return this.store.clearNotesCategory((String) user, name)
                        .then(wrap((ResponseEntity) ResponseEntity.ok(Map.of("name", name))));
            }
            return this.badRequest("缺少 id 或 name");
        });
    }

    // PUT /categories/reorder：按 ids 顺序回写 order
    @PutMapping(value = {"/categories/reorder"})
    public Mono<ResponseEntity> reorderCategories(@RequestHeader(value = "X-PasswordBook-Token") String token,
                                                  @RequestBody Map<String, Object> body) {
        return this.currentUser().flatMap(user -> {
            SecretKey key = this.authedKey(token, (String) user);
            if (key == null) {
                return this.unauthorized();
            }
            Object idsObj = body.get("ids");
            if (!(idsObj instanceof List)) {
                return this.badRequest("缺少 ids");
            }
            List<String> idList = ((List<?>) idsObj).stream().map(Object::toString).collect(Collectors.toList());
            return Flux.range(0, idList.size()).flatMap(i -> {
                String cid = idList.get(i);
                return this.store.getCategory(cid).flatMap(c -> {
                    if (c.getSpec().getOwner() == null || !user.equals(c.getSpec().getOwner())) {
                        return Mono.empty();
                    }
                    c.getSpec().setOrder(i + 1);
                    return this.store.updateCategory(c);
                });
            }).then(wrap((ResponseEntity) ResponseEntity.ok(Map.of("ok", true))));
        });
    }

    private PasswordBookCategory newCategory(String user, String name, int order) {
        PasswordBookCategory cat = new PasswordBookCategory();
        cat.setMetadata((MetadataOperator) new Metadata());
        cat.getMetadata().setName("pbc-" + UUID.randomUUID().toString().replace("-", ""));
        PasswordBookCategory.Spec spec = new PasswordBookCategory.Spec();
        spec.setName(name);
        spec.setOwner(user);
        spec.setOrder(order);
        cat.setSpec(spec);
        return cat;
    }

    private Map<String, Object> catToMap(PasswordBookCategory c, boolean managed) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", c.getMetadata().getName());
        m.put("name", c.getSpec().getName());
        m.put("order", c.getSpec().getOrder());
        m.put("managed", managed);
        return m;
    }

    private List<Map<String, Object>> mergeCategories(List<PasswordBookCategory> managed, List<String> virtuals) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        Set<String> managedNames = new HashSet<String>();
        for (PasswordBookCategory c : managed) {
            out.add(this.catToMap(c, true));
            managedNames.add(c.getSpec().getName());
        }
        for (String vn : virtuals) {
            if (!managedNames.contains(vn)) {
                LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("id", null);
                m.put("name", vn);
                m.put("order", null);
                m.put("managed", false);
                out.add(m);
            }
        }
        return out;
    }

    // 默认拒绝：owner 为空（遗留/归属不明）的记录不可读，避免越权访问（审核指南 4.3.1/4.3.2）
    private boolean canRead(PasswordBookNote n, String user) {
        String owner = n.getSpec().getOwner();
        return owner != null && user.equals(owner);
    }

    private String plainText(String html) {
        if (html == null) {
            return "";
        }
        String s = html.replaceAll("(?s)<[^>]+>", " ").replace("&nbsp;", " ")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'");
        return s.replaceAll("\\s+", " ").trim();
    }
}
