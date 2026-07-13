package cn.miaohaha.passwordbook;

import cn.miaohaha.passwordbook.extension.PasswordBookMeta;
import cn.miaohaha.passwordbook.extension.PasswordBookNote;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * 加密记事本插件入口。
 * 注册 Note / Meta 两个扩展实体的 Scheme；Category 由框架按 @GVK 自动扫描注册。
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
    }
}
