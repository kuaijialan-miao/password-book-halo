/*
 * 加密记事本 — 分类扩展实体。
 * 与 PasswordBookNote 同款模式：Halo 插件框架按 @GVK 自动扫描注册。
 */
package cn.miaohaha.passwordbook.extension;

import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@GVK(group = "passwordbook.halo.run", version = "v1alpha1",
        kind = "PasswordBookCategory", singular = "passwordbookcategory", plural = "passwordbookcategories")
public class PasswordBookCategory extends AbstractExtension {
    private Spec spec;

    public PasswordBookCategory() {
    }

    public Spec getSpec() {
        return spec;
    }

    public void setSpec(Spec spec) {
        this.spec = spec;
    }

    public static class Spec {
        private String name;   // 分类显示名（明文标签，非加密内容）
        private String owner;  // 用户名，用于多用户隔离
        private Integer order; // 排序权重，越小越靠前

        public Spec() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        public Integer getOrder() {
            return order;
        }

        public void setOrder(Integer order) {
            this.order = order;
        }
    }
}
