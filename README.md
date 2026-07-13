# password-book（加密记事本）— Halo 插件反编译快照

本仓库是 Halo 2.x 插件 **password-book**（显示名「加密记事本」）v1.2.1 的**反编译源码快照**。

## 来源
- 原始安装包：`password-book-1.2.1.jar`（作者 miaohaha，站点 https://miaohaha.cn）
- 许可证：`Proprietary`（专有软件，保留所有权利，详见 LICENSE）

## 本仓库内容
- `src/main/java/cn/miaohaha/passwordbook/`：由 jar 内 `.class` 经 CFR 反编译器还原的 Java 源码（7 个文件，约 1158 行）
- `console/`、`extensions/`、`plugin.yaml`、`logo.png`：插件资源与元数据
- `password-book-1.2.1.jar`：原始可安装包备份

## 说明
- 反编译所得源码**无法 1:1 还原为原始工程**（原始 `build.gradle` / 前端 `.vue` 源码不在 jar 内），仅供阅读、学习、审计使用。
- 加密实现：AES-256-GCM + PBKDF2（60 万次迭代），见 `service/CryptoService.java`。
- 前端 `console/` 下的 `PasswordBook.*.js` 为 webpack 压缩构建产物，非可读源码。

## 许可证
插件为专有软件（Proprietary）。未经作者书面授权，不得用于商业分发或再发布。本仓库提供的反编译源码仅供学习与研究参考。
