/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 *  run.halo.app.extension.AbstractExtension
 *  run.halo.app.extension.GVK
 */
package cn.miaohaha.passwordbook.extension;

import java.util.Map;
import lombok.Generated;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@GVK(group="passwordbook.halo.run", version="v1alpha1", kind="PasswordBookMeta", singular="passwordbookmeta", plural="passwordbookmetas")
public class PasswordBookMeta
extends AbstractExtension {
    private Spec spec;

    @Generated
    public PasswordBookMeta() {
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
        return "PasswordBookMeta(spec=" + String.valueOf(this.getSpec()) + ")";
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof PasswordBookMeta)) {
            return false;
        }
        PasswordBookMeta other = (PasswordBookMeta)((Object)o);
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
        return other instanceof PasswordBookMeta;
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
        private Map<String, String> data;

        @Generated
        public Spec() {
        }

        @Generated
        public Map<String, String> getData() {
            return this.data;
        }

        @Generated
        public void setData(Map<String, String> data) {
            this.data = data;
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
            Map<String, String> this$data = this.getData();
            Map<String, String> other$data = other.getData();
            return !(this$data == null ? other$data != null : !((Object)this$data).equals(other$data));
        }

        @Generated
        protected boolean canEqual(Object other) {
            return other instanceof Spec;
        }

        @Generated
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Map<String, String> $data = this.getData();
            result = result * 59 + ($data == null ? 43 : ((Object)$data).hashCode());
            return result;
        }

        @Generated
        public String toString() {
            return "PasswordBookMeta.Spec(data=" + String.valueOf(this.getData()) + ")";
        }
    }
}

