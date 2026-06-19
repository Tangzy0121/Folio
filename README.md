# Folio

一个**纯本地、隐私优先**的 Android 阅读器,用于查看 Markdown / HTML / 网页包(ZIP)。原生 Kotlin 编写,界面走电子书式极简风,无账号、无云端、无追踪。

> A local-first, privacy-friendly Android reader for Markdown / HTML / zipped web pages. Native Kotlin, e-book-style minimal UI, no account, no cloud, no tracking.

## ✨ 功能

**阅读**
- Markdown 全语法渲染:标题 / 列表 / 任务列表 / 表格 / 引用 / 删除线 / 链接 / 图片
- LaTeX 块公式(`$$ … $$`),长公式 / 宽表格 / 长代码行**横向滚动**不挤压
- 代码块**语法高亮**(Python · JS/TS · Kotlin · Java · C/C++ · SQL · Go · Rust)+ 可选配色(跟随 / GitHub / Dracula / Nord)
- 排版精修(标题 / 引用竖条 / 分割线 / 行内码),观感向 Typora 靠拢
- HTML 查看(JavaScript 默认关闭,可一键开启)
- ZIP 网页包:解压后用 `WebViewAssetLoader` 正确加载相对资源,带 Zip-Slip 防护
- **阅读主题** 9 套(纸白 / 米黄 / 青墨 / 夜黑 / 渐变风景…)+ 字体 / 字号
- 目录(TOC)跳转 · 阅读进度记忆 · 图片点击全屏捏合放大

**管理**
- 最近 / 收藏 · **多标签**(一个文件多标签,可改名 / 删除 / 多选筛选)
- 轻量编辑(Markdown,改写库内副本并可导出)
- 重命名 / 删除 / 分享(文本 + 文件)· 全局浅色 / 深色 / 跟随系统
- 所有弹窗为 Material 底部抽屉

## 🧱 技术栈

| 方面 | 选型 |
|---|---|
| 语言 / UI | Kotlin · XML Views(viewBinding,**非 Compose**) |
| 构建 | AGP 9.2.1 · Gradle 9.4.1 · Kotlin 2.2.10 · compileSdk 36 · minSdk 24 |
| Markdown | [Markwon](https://github.com/noties/Markwon) 4.6.2(+ ext-latex / recycler-table) |
| 代码高亮 | 自写轻量词法分析(无注解处理器,零额外重依赖) |
| HTML / ZIP | 系统 WebView + `androidx.webkit` WebViewAssetLoader |
| 存储 | 本地 JSON(`org.json`,**不用 Room/KSP**) |

## 🔨 构建

需要 Android Studio(自带 JBR)或本机 JDK 21。

```bash
# 用 Android Studio 的 JBR 当 JAVA_HOME(Windows 示例)
JAVA_HOME="<Android Studio>/jbr" ./gradlew :app:assembleDebug
```

产物在 `app/build/outputs/apk/debug/`。也可直接在 Android Studio 里 Run。

## 📱 安装

目标设备 Android 7.0+（minSdk 24）。把 debug APK 拖进手机安装即可。

## 📂 目录

```
app/src/main/java/com/folio/reader/
  ├─ data/     # 库记录 + JSON 持久化 + 偏好
  ├─ file/     # 类型识别 / 复制入库 / ZIP 解压 / Intent
  ├─ reader/   # Markwon 装配 / 代码高亮 / 阅读主题
  └─ ui/       # 首页 + 阅读页 + 各 Material 抽屉
design/        # 渲染测试样例 test.md / test.html + 交互原型 demo
```

## 📄 许可

[MIT](./LICENSE) © Tangzy0121
