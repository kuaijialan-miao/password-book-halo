/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Repository
 *  reactor.core.publisher.Flux
 *  reactor.core.publisher.Mono
 *  run.halo.app.extension.Extension
 *  run.halo.app.extension.Metadata
 *  run.halo.app.extension.MetadataOperator
 *  run.halo.app.extension.ReactiveExtensionClient
 */
package cn.miaohaha.passwordbook.repository;

import cn.miaohaha.passwordbook.extension.PasswordBookCategory;
import cn.miaohaha.passwordbook.extension.PasswordBookMeta;
import cn.miaohaha.passwordbook.extension.PasswordBookNote;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Extension;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.extension.ReactiveExtensionClient;

@Repository
public class PasswordBookStore {
    private static final String META_NAME = "password-book-default";
    private final ReactiveExtensionClient client;
    private static final Comparator<PasswordBookNote> BY_UPDATED_DESC = Comparator.comparing(n -> n.getSpec() == null ? null : n.getSpec().getUpdatedAt(), Comparator.nullsLast(Comparator.reverseOrder()));

    public PasswordBookStore(ReactiveExtensionClient client) {
        this.client = client;
    }

    public Mono<String> getMeta(String key) {
        return this.client.fetch(PasswordBookMeta.class, META_NAME).flatMap(m -> {
            if (m.getSpec() == null || m.getSpec().getData() == null) {
                return Mono.empty();
            }
            String value = m.getSpec().getData().get(key);
            return value != null ? Mono.just(value) : Mono.empty();
        });
    }

    public Mono<Void> setMeta(String key, String value) {
        return this.client.fetch(PasswordBookMeta.class, META_NAME).flatMap(meta -> {
            if (meta.getSpec() == null) {
                meta.setSpec(new PasswordBookMeta.Spec());
            }
            if (meta.getSpec().getData() == null) {
                meta.getSpec().setData(new HashMap<String, String>());
            }
            meta.getSpec().getData().put(key, value);
            return this.client.update(meta);
        }).switchIfEmpty(Mono.defer(() -> {
            PasswordBookMeta meta = new PasswordBookMeta();
            meta.setMetadata((MetadataOperator)new Metadata());
            meta.getMetadata().setName(META_NAME);
            PasswordBookMeta.Spec spec = new PasswordBookMeta.Spec();
            spec.setData(new HashMap<String, String>());
            spec.getData().put(key, value);
            meta.setSpec(spec);
            return this.client.create(meta);
        })).then();
    }

    public Mono<Void> deleteMeta(String key) {
        return this.client.fetch(PasswordBookMeta.class, META_NAME).flatMap(meta -> {
            if (meta.getSpec() == null || meta.getSpec().getData() == null) {
                return Mono.empty();
            }
            meta.getSpec().getData().remove(key);
            return this.client.update(meta);
        }).then();
    }

    public Flux<PasswordBookNote> listNotes() {
        return this.client.list(PasswordBookNote.class, n -> n.getSpec() != null, BY_UPDATED_DESC).sort(BY_UPDATED_DESC);
    }

    public Mono<PasswordBookNote> getNote(String id) {
        return this.client.fetch(PasswordBookNote.class, id);
    }

    public Mono<PasswordBookNote> createNote(PasswordBookNote note) {
        return this.client.create(note);
    }

    public Mono<PasswordBookNote> updateNote(PasswordBookNote note) {
        return this.client.update(note);
    }

    public Mono<Void> deleteNote(String id) {
        return this.client.fetch(PasswordBookNote.class, id).flatMap(arg_0 -> ((ReactiveExtensionClient)this.client).delete(arg_0)).then();
    }

    // ===== 分类（PasswordBookCategory）=====

    private static final Comparator<PasswordBookCategory> CAT_BY_ORDER =
            Comparator.comparing(c -> c.getSpec() == null || c.getSpec().getOrder() == null
                    ? Integer.MAX_VALUE : c.getSpec().getOrder());

    public Flux<PasswordBookCategory> listCategories(String owner) {
        return this.client.list(PasswordBookCategory.class,
                c -> c.getSpec() != null && owner.equals(c.getSpec().getOwner()), CAT_BY_ORDER);
    }

    public Mono<PasswordBookCategory> getCategory(String id) {
        return this.client.fetch(PasswordBookCategory.class, id);
    }

    public Mono<PasswordBookCategory> createCategory(PasswordBookCategory cat) {
        return this.client.create(cat);
    }

    public Mono<PasswordBookCategory> updateCategory(PasswordBookCategory cat) {
        return this.client.update(cat);
    }

    public Mono<Void> deleteCategory(String id) {
        return this.client.fetch(PasswordBookCategory.class, id)
                .flatMap(c -> this.client.delete(c)).then();
    }

    // 聚合当前用户笔记中出现过的分类名（用于 virtual 标签，保证旧笔记的分类也能显示）
    public Mono<List<String>> listNoteCategories(String owner) {
        return this.listNotes()
                .filter(n -> owner.equals(n.getSpec().getOwner())
                        && n.getSpec().getCategory() != null && !n.getSpec().getCategory().isEmpty())
                .map(n -> n.getSpec().getCategory())
                .distinct()
                .sort()
                .collectList();
    }

    // 批量把某分类下的笔记改名（改名分类时回写）
    public Mono<Void> reassignNotesCategory(String owner, String oldName, String newName) {
        return this.listNotes()
                .filter(n -> owner.equals(n.getSpec().getOwner()) && oldName.equals(n.getSpec().getCategory()))
                .flatMap(n -> {
                    n.getSpec().setCategory(newName);
                    return this.updateNote(n);
                }, 4)
                .then();
    }

    // 批量把某分类下的笔记置为未分类（删除分类时）
    public Mono<Void> clearNotesCategory(String owner, String name) {
        return this.listNotes()
                .filter(n -> owner.equals(n.getSpec().getOwner()) && name.equals(n.getSpec().getCategory()))
                .flatMap(n -> {
                    n.getSpec().setCategory("");
                    return this.updateNote(n);
                }, 4)
                .then();
    }
}

