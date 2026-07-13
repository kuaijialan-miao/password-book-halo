/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 *  run.halo.app.extension.AbstractExtension
 *  run.halo.app.extension.GVK
 */
package cn.miaohaha.passwordbook.extension;

import lombok.Generated;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@GVK(group="passwordbook.halo.run", version="v1alpha1", kind="PasswordBookNote", singular="passwordbooknote", plural="passwordbooknotes")
public class PasswordBookNote
extends AbstractExtension {
    private Spec spec;

    @Generated
    public PasswordBookNote() {
    }

    @Generated
    public Spec getSpec() {
        return this.spec;
    }

    @Generated
    public void setSpec(Spec spec) {
        this.spec = spec;
    }

    @Generated
    public String toString() {
        return "PasswordBookNote(spec=" + String.valueOf(this.getSpec()) + ")";
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof PasswordBookNote)) {
            return false;
        }
        PasswordBookNote other = (PasswordBookNote)((Object)o);
        if (!other.canEqual((Object)this)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        Spec this$spec = this.getSpec();
        Spec other$spec = other.getSpec();
        return !(this$spec == null ? other$spec != null : !((Object)this$spec).equals(other$spec));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof PasswordBookNote;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = super.hashCode();
        Spec $spec = this.getSpec();
        result = result * 59 + ($spec == null ? 43 : ((Object)$spec).hashCode());
        return result;
    }

    public static class Spec {
        private String titleData;
        private String contentData;
        private String contentType;
        private String owner;
        private String category;
        private Long createdAt;
        private Long updatedAt;

        @Generated
        public Spec() {
        }

        @Generated
        public String getTitleData() {
            return this.titleData;
        }

        @Generated
        public String getContentData() {
            return this.contentData;
        }

        @Generated
        public String getContentType() {
            return this.contentType;
        }

        @Generated
        public String getOwner() {
            return this.owner;
        }

        @Generated
        public String getCategory() {
            return this.category;
        }

        @Generated
        public Long getCreatedAt() {
            return this.createdAt;
        }

        @Generated
        public Long getUpdatedAt() {
            return this.updatedAt;
        }

        @Generated
        public void setTitleData(String titleData) {
            this.titleData = titleData;
        }

        @Generated
        public void setContentData(String contentData) {
            this.contentData = contentData;
        }

        @Generated
        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        @Generated
        public void setOwner(String owner) {
            this.owner = owner;
        }

        @Generated
        public void setCategory(String category) {
            this.category = category;
        }

        @Generated
        public void setCreatedAt(Long createdAt) {
            this.createdAt = createdAt;
        }

        @Generated
        public void setUpdatedAt(Long updatedAt) {
            this.updatedAt = updatedAt;
        }

        @Generated
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof Spec)) {
                return false;
            }
            Spec other = (Spec)o;
            if (!other.canEqual(this)) {
                return false;
            }
            Long this$createdAt = this.getCreatedAt();
            Long other$createdAt = other.getCreatedAt();
            if (this$createdAt == null ? other$createdAt != null : !((Object)this$createdAt).equals(other$createdAt)) {
                return false;
            }
            Long this$updatedAt = this.getUpdatedAt();
            Long other$updatedAt = other.getUpdatedAt();
            if (this$updatedAt == null ? other$updatedAt != null : !((Object)this$updatedAt).equals(other$updatedAt)) {
                return false;
            }
            String this$titleData = this.getTitleData();
            String other$titleData = other.getTitleData();
            if (this$titleData == null ? other$titleData != null : !this$titleData.equals(other$titleData)) {
                return false;
            }
            String this$contentData = this.getContentData();
            String other$contentData = other.getContentData();
            if (this$contentData == null ? other$contentData != null : !this$contentData.equals(other$contentData)) {
                return false;
            }
            String this$contentType = this.getContentType();
            String other$contentType = other.getContentType();
            if (this$contentType == null ? other$contentType != null : !this$contentType.equals(other$contentType)) {
                return false;
            }
            String this$owner = this.getOwner();
            String other$owner = other.getOwner();
            return !(this$owner == null ? other$owner != null : !this$owner.equals(other$owner));
        }

        @Generated
        protected boolean canEqual(Object other) {
            return other instanceof Spec;
        }

        @Generated
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Long $createdAt = this.getCreatedAt();
            result = result * 59 + ($createdAt == null ? 43 : ((Object)$createdAt).hashCode());
            Long $updatedAt = this.getUpdatedAt();
            result = result * 59 + ($updatedAt == null ? 43 : ((Object)$updatedAt).hashCode());
            String $titleData = this.getTitleData();
            result = result * 59 + ($titleData == null ? 43 : $titleData.hashCode());
            String $contentData = this.getContentData();
            result = result * 59 + ($contentData == null ? 43 : $contentData.hashCode());
            String $contentType = this.getContentType();
            result = result * 59 + ($contentType == null ? 43 : $contentType.hashCode());
            String $owner = this.getOwner();
            result = result * 59 + ($owner == null ? 43 : $owner.hashCode());
            return result;
        }

        @Generated
        public String toString() {
            return "PasswordBookNote.Spec(titleData=" + this.getTitleData() + ", contentData=" + this.getContentData() + ", contentType=" + this.getContentType() + ", owner=" + this.getOwner() + ", createdAt=" + this.getCreatedAt() + ", updatedAt=" + this.getUpdatedAt() + ")";
        }
    }
}

