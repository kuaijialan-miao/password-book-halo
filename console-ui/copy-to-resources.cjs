// 将打包产物 build/dist 拷贝到 src/main/resources/console，供 Gradle/手动打包纳入插件 jar。
const fs = require("fs");
const path = require("path");

const src = path.resolve(__dirname, "build/dist");
const dest = path.resolve(__dirname, "../src/main/resources/console");

function copyDir(s, d) {
  fs.mkdirSync(d, { recursive: true });
  for (const e of fs.readdirSync(s, { withFileTypes: true })) {
    const sp = path.join(s, e.name);
    const dp = path.join(d, e.name);
    if (e.isDirectory()) copyDir(sp, dp);
    else fs.copyFileSync(sp, dp);
  }
}

if (!fs.existsSync(src)) {
  console.error("未找到构建产物目录:", src, "\n请先运行 npm run build");
  process.exit(1);
}
copyDir(src, dest);
console.log("已拷贝前端产物到:", dest);
