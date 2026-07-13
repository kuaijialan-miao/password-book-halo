package cn.miaohaha.passwordbook.extension;

import java.util.Map;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/**
 * 加密记事本元数据实体。
 * 存储每用户的 master_salt / verifier / must_change 等派生密钥与状态标记。
 */
@GVK(group = "passwordbook.halo.run", version = "v1alpha1",
        kind = "PasswordBookMeta", singular = "passwordbookmeta", plural = "passwordbookmetas")
public class PasswordBookMeta extends AbstractExtension {
    private Spec spec;

    public PasswordBookMeta() {
    }

    public Spec getSpec() {
        return spec;
    }

    public void setSpec(Spec spec) {
        this.spec = spec;
    }

    public static class Spec {
        private Map<String, String> data;

        public Spec() {
        }

        public Map<String, String> getData() {
            return data;
        }

        public void setData(Map<String, String> data) {
            this.data = data;
        }
    }
}
