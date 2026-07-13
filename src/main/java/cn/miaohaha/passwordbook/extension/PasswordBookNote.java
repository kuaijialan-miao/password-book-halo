package cn.miaohaha.passwordbook.extension;

import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/**
 * 加密记事本笔记实体。
 * titleData / contentData / contentType 为加密密文，其余字段为元数据（含 owner 隔离标记）。
 */
@GVK(group = "passwordbook.halo.run", version = "v1alpha1",
        kind = "PasswordBookNote", singular = "passwordbooknote", plural = "passwordbooknotes")
public class PasswordBookNote extends AbstractExtension {
    private Spec spec;

    public PasswordBookNote() {
    }

    public Spec getSpec() {
        return spec;
    }

    public void setSpec(Spec spec) {
        this.spec = spec;
    }

    public static class Spec {
        private String titleData;
        private String contentData;
        private String contentType;
        private String owner;
        private String category;
        private Long createdAt;
        private Long updatedAt;

        public Spec() {
        }

        public String getTitleData() {
            return titleData;
        }

        public void setTitleData(String titleData) {
            this.titleData = titleData;
        }

        public String getContentData() {
            return contentData;
        }

        public void setContentData(String contentData) {
            this.contentData = contentData;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public Long getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Long createdAt) {
            this.createdAt = createdAt;
        }

        public Long getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(Long updatedAt) {
            this.updatedAt = updatedAt;
        }
    }
}
