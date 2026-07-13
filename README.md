# password-book（加密记事本）— Halo 插件反编译快照

本仓库是 Halo 2.x 插件 **password-book**（显示名「加密记事本」）v1.2.1 的**反编译源码快照**。

## 来源

- 原始安装包：`password-book-1.2.1.jar`（作者 miaohaha，站点 <https://miaohaha.cn）>
- 许可证：**GPL-3.0**（开源，详见 [LICENSE](LICENSE)）

## 本仓库内容

- `src/main/java/cn/miaohaha/passwordbook/`：由 jar 内 `.class` 经 CFR 反编译器还原的 Java 源码（7 个文件，约 1158 行）
- `console/`、`extensions/`、`plugin.yaml`、`logo.png`：插件资源与元数据
- `password-book-1.2.1.jar`：原始可安装包备份

## 说明

- 反编译所得源码**无法 1:1 还原为原始工程**（原始 `build.gradle` / 前端 `.vue` 源码不在 jar 内），仅供阅读、学习、审计使用。
- 加密实现：AES-256-GCM + PBKDF2（60 万次迭代），见 `service/CryptoService.java`。
- 前端 `console/` 下的 `PasswordBook.*.js` 为 webpack 压缩构建产物，非可读源码。

## 许可证

本项目以 **GNU General Public License v3.0（GPL-3.0）** 开源。任何人可自由使用、复制、修改、发布本仓库代码；但如果你**分发**修改后的版本（无论源码还是编译产物），必须同样以 GPL-3.0 开源你的修改，且保留版权与许可声明。原始插件 `password-book` 由 miaohaha 开发，现以 GPL-3.0 协议发布源码供社区使用。

