import { viteConfig } from "@halo-dev/ui-plugin-bundler-kit";

// 复用 Halo 官方打包器：自动 externals vue/axios、设置 console 入口与资产路径。
// manifestPath 指向插件 plugin.yaml（用于确定插件名与资产 base）。
export default viteConfig({
  manifestPath: "../plugin.yaml",
  vite: {},
});
