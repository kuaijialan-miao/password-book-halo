/*
 * 加密记事本 — 分类扩展实体。
 * 与 PasswordBookNote 同款模式：Halo 插件框架按 @GVK 自动扫描注册。
 */
package cn.miaohaha.passwordbook.extension;

import lombok.Generated;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@GVK(group = "passwordbook.halo.run", version = "v1alpha1",
        kind = "PasswordBookCategory", singular = "passwordbookcategory", plural = "passwordbookcategories")
public class PasswordBookCategory extends AbstractExtension {
    private Spec spec;

    @Generated
    public PasswordBookCategory() {
    }

    @Generated
    public Spec getSpec() {
        return this.spec;
    }

    @Generated
    public void setSpec(Spec spec) {
        this.spec = spec;
    }

    public static class Spec {
        private String name;   // 分类显示名（明文标签，非加密内容）
        private String owner;  // 用户名，用于多用户隔离
        private Integer order; // 排序权重，越小越靠前

        @Generated
        public Spec() {
        }

        @Generated
        public String getName() {
            return this.name;
        }

        @Generated
        public void setName(String name) {
            this.name = name;
        }

        @Generated
        public String getOwner() {
            return this.owner;
        }

        @Generated
        public void setOwner(String owner) {
            this.owner = owner;
        }

        @Generated
        public Integer getOrder() {
            return this.order;
        }

        @Generated
        public void setOrder(Integer order) {
            this.order = order;
        }
    }
}
