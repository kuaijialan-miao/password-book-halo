/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Component
 *  run.halo.app.extension.Scheme
 *  run.halo.app.extension.SchemeManager
 *  run.halo.app.plugin.BasePlugin
 *  run.halo.app.plugin.PluginContext
 */
package cn.miaohaha.passwordbook;

import cn.miaohaha.passwordbook.extension.PasswordBookMeta;
import cn.miaohaha.passwordbook.extension.PasswordBookNote;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

@Component
public class PasswordBookPlugin
extends BasePlugin {
    private final SchemeManager schemeManager;

    public PasswordBookPlugin(PluginContext pluginContext, SchemeManager schemeManager) {
        super(pluginContext);
        this.schemeManager = schemeManager;
    }

    public void start() {
        this.schemeManager.register(PasswordBookNote.class);
        this.schemeManager.register(PasswordBookMeta.class);
        System.out.println("PasswordBook plugin started");
    }

    public void stop() {
        Scheme metaScheme;
        Scheme noteScheme = this.schemeManager.get(PasswordBookNote.class);
        if (noteScheme != null) {
            this.schemeManager.unregister(noteScheme);
        }
        if ((metaScheme = this.schemeManager.get(PasswordBookMeta.class)) != null) {
            this.schemeManager.unregister(metaScheme);
        }
        System.out.println("PasswordBook plugin stopped");
    }
}

