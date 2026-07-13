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

import cn.miaohaha.passwordbook.extension.PasswordBookMeta;
import cn.miaohaha.passwordbook.extension.PasswordBookNote;
import java.util.Comparator;
import java.util.HashMap;
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
            return value != null ? Mono.just((Object)value) : Mono.empty();
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
            return this.client.update((Extension)meta);
        }).switchIfEmpty(Mono.defer(() -> {
            PasswordBookMeta meta = new PasswordBookMeta();
            meta.setMetadata((MetadataOperator)new Metadata());
            meta.getMetadata().setName(META_NAME);
            PasswordBookMeta.Spec spec = new PasswordBookMeta.Spec();
            spec.setData(new HashMap<String, String>());
            spec.getData().put(key, value);
            meta.setSpec(spec);
            return this.client.create((Extension)meta);
        })).then();
    }

    public Mono<Void> deleteMeta(String key) {
        return this.client.fetch(PasswordBookMeta.class, META_NAME).flatMap(meta -> {
            if (meta.getSpec() == null || meta.getSpec().getData() == null) {
                return Mono.empty();
            }
            meta.getSpec().getData().remove(key);
            return this.client.update((Extension)meta);
        }).then();
    }

    public Flux<PasswordBookNote> listNotes() {
        return this.client.list(PasswordBookNote.class, n -> n.getSpec() != null, BY_UPDATED_DESC).sort(BY_UPDATED_DESC);
    }

    public Mono<PasswordBookNote> getNote(String id) {
        return this.client.fetch(PasswordBookNote.class, id);
    }

    public Mono<PasswordBookNote> createNote(PasswordBookNote note) {
        return this.client.create((Extension)note);
    }

    public Mono<PasswordBookNote> updateNote(PasswordBookNote note) {
        return this.client.update((Extension)note);
    }

    public Mono<Void> deleteNote(String id) {
        return this.client.fetch(PasswordBookNote.class, id).flatMap(arg_0 -> ((ReactiveExtensionClient)this.client).delete(arg_0)).then();
    }
}

