package cn.miaohaha.passwordbook;

import cn.miaohaha.passwordbook.extension.PasswordBookCategory;
import cn.miaohaha.passwordbook.extension.PasswordBookMeta;
import cn.miaohaha.passwordbook.extension.PasswordBookNote;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * 加密记事本插件入口。
 * 注册 Note / Meta / Category 三个扩展实体的 Scheme。
 * （注：Halo 不会自动按 @GVK 扫描插件内扩展，必须显式 register，否则分类等功能会报
 *  "Scheme not found for ...PasswordBookCategory"。）
 */
@Component
public class PasswordBookPlugin extends BasePlugin {
    private final SchemeManager schemeManager;

    public PasswordBookPlugin(PluginContext pluginContext, SchemeManager schemeManager) {
        super(pluginContext);
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        schemeManager.register(PasswordBookNote.class);
        schemeManager.register(PasswordBookMeta.class);
        schemeManager.register(PasswordBookCategory.class);
    }

    @Override
    public void stop() {
        Scheme noteScheme = schemeManager.get(PasswordBookNote.class);
        if (noteScheme != null) {
            schemeManager.unregister(noteScheme);
        }
        Scheme metaScheme = schemeManager.get(PasswordBookMeta.class);
        if (metaScheme != null) {
            schemeManager.unregister(metaScheme);
        }
        Scheme categoryScheme = schemeManager.get(PasswordBookCategory.class);
        if (categoryScheme != null) {
            schemeManager.unregister(categoryScheme);
        }
    }
}
