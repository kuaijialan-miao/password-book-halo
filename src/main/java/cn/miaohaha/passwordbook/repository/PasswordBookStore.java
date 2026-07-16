package cn.miaohaha.passwordbook.repository;

import cn.miaohaha.passwordbook.extension.PasswordBookCategory;
import cn.miaohaha.passwordbook.extension.PasswordBookMeta;
import cn.miaohaha.passwordbook.extension.PasswordBookNote;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import reactor.util.retry.Retry;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.extension.ReactiveExtensionClient;

/**
 * 加密记事本数据访问层。
 * 封装对 Note / Meta / Category 三类 Halo 扩展实体的响应式读写。
 * 所有笔记与分类接口按 owner（用户名）做多用户隔离。
 */
@Repository
public class PasswordBookStore {
    private static final String META_NAME = "password-book-default";
    private final ReactiveExtensionClient client;

    private static final Comparator<PasswordBookNote> BY_UPDATED_DESC =
            Comparator.comparing(n -> n.getSpec() == null ? null : n.getSpec().getUpdatedAt(),
                    Comparator.nullsLast(Comparator.reverseOrder()));

    private static final Comparator<PasswordBookCategory> CAT_BY_ORDER =
            Comparator.comparing(c -> c.getSpec() == null || c.getSpec().getOrder() == null
                    ? Integer.MAX_VALUE : c.getSpec().getOrder());

    public PasswordBookStore(ReactiveExtensionClient client) {
        this.client = client;
    }

    // ===== 元数据 =====

    public Mono<String> getMeta(String key) {
        return client.fetch(PasswordBookMeta.class, META_NAME).flatMap(m -> {
            if (m.getSpec() == null || m.getSpec().getData() == null) {
                return Mono.empty();
            }
            String value = m.getSpec().getData().get(key);
            return value != null ? Mono.just(value) : Mono.empty();
        });
    }

    public Mono<Void> setMeta(String key, String value) {
        // 所有用户共用同一 Meta 文档，read-modify-write 非原子；通过有限重试消除并发丢失更新。
        // 重试会重新 fetch 最新文档再 put，写入是幂等的，安全。
        return client.fetch(PasswordBookMeta.class, META_NAME).flatMap(meta -> {
            if (meta.getSpec() == null) {
                meta.setSpec(new PasswordBookMeta.Spec());
            }
            if (meta.getSpec().getData() == null) {
                meta.getSpec().setData(new HashMap<>());
            }
            meta.getSpec().getData().put(key, value);
            return client.update(meta);
        }).switchIfEmpty(Mono.defer(() -> {
            PasswordBookMeta meta = new PasswordBookMeta();
            meta.setMetadata((MetadataOperator) new Metadata());
            meta.getMetadata().setName(META_NAME);
            PasswordBookMeta.Spec spec = new PasswordBookMeta.Spec();
            spec.setData(new HashMap<>());
            spec.getData().put(key, value);
            meta.setSpec(spec);
            return client.create(meta);
        })).retryWhen(Retry.max(3)).then();
    }

    public Mono<Void> deleteMeta(String key) {
        return client.fetch(PasswordBookMeta.class, META_NAME).flatMap(meta -> {
            if (meta.getSpec() == null || meta.getSpec().getData() == null) {
                return Mono.empty();
            }
            meta.getSpec().getData().remove(key);
            return client.update(meta);
        }).then();
    }

    // ===== 笔记 =====

    public Flux<PasswordBookNote> listNotes() {
        return client.list(PasswordBookNote.class, n -> n.getSpec() != null, BY_UPDATED_DESC)
                .sort(BY_UPDATED_DESC);
    }

    public Mono<PasswordBookNote> getNote(String id) {
        return client.fetch(PasswordBookNote.class, id);
    }

    public Mono<PasswordBookNote> createNote(PasswordBookNote note) {
        return client.create(note);
    }

    public Mono<PasswordBookNote> updateNote(PasswordBookNote note) {
        return client.update(note);
    }

    public Mono<Void> deleteNote(String id) {
        return client.fetch(PasswordBookNote.class, id)
                .flatMap(note -> client.delete(note)).then();
    }

    // ===== 分类（PasswordBookCategory）=====

    public Flux<PasswordBookCategory> listCategories(String owner) {
        return client.list(PasswordBookCategory.class,
                c -> c.getSpec() != null && owner.equals(c.getSpec().getOwner()), CAT_BY_ORDER);
    }

    public Mono<PasswordBookCategory> getCategory(String id) {
        return client.fetch(PasswordBookCategory.class, id);
    }

    public Mono<PasswordBookCategory> createCategory(PasswordBookCategory cat) {
        return client.create(cat);
    }

    public Mono<PasswordBookCategory> updateCategory(PasswordBookCategory cat) {
        return client.update(cat);
    }

    public Mono<Void> deleteCategory(String id) {
        return client.fetch(PasswordBookCategory.class, id)
                .flatMap(c -> client.delete(c)).then();
    }

    // 聚合当前用户笔记中出现过的分类名（用于 virtual 标签，保证旧笔记的分类也能显示）
    public Mono<List<String>> listNoteCategories(String owner) {
        return listNotes()
                .filter(n -> owner.equals(n.getSpec().getOwner())
                        && n.getSpec().getCategory() != null && !n.getSpec().getCategory().isEmpty())
                .map(n -> n.getSpec().getCategory())
                .distinct()
                .sort()
                .collectList();
    }

    // 批量把某分类下的笔记改名（改名分类时回写）
    public Mono<Void> reassignNotesCategory(String owner, String oldName, String newName) {
        return listNotes()
                .filter(n -> owner.equals(n.getSpec().getOwner()) && oldName.equals(n.getSpec().getCategory()))
                .flatMap(n -> {
                    n.getSpec().setCategory(newName);
                    return updateNote(n);
                }, 4)
                .then();
    }

    // 批量把某分类下的笔记置为未分类（删除分类时）
    public Mono<Void> clearNotesCategory(String owner, String name) {
        return listNotes()
                .filter(n -> owner.equals(n.getSpec().getOwner()) && name.equals(n.getSpec().getCategory()))
                .flatMap(n -> {
                    n.getSpec().setCategory("");
                    return updateNote(n);
                }, 4)
                .then();
    }
}
