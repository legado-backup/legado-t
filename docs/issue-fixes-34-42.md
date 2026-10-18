# Issue #34–#42 修复与回归说明

## 功能入口

- #39：音频播放页右上角菜单 →「播放速度」，提供 0.5 / 0.75 / 1 / 1.25 / 1.5 / 2 / 3 倍；原有 ±0.1 倍按钮保留。
- #38：设置 → 其它设置 →「使用旧版发现页」。默认仍为新版；旧版按书源展开分类，保留搜索、分组、登录、编辑、刷新与删除。
- #37：阅读记录显示作者和封面，保存书籍 URL、作者、封面和图片来源快照。删除书籍后保留历史快照；同名不同 URL 的书分别统计。
- #35：v1 / v2 登录 UI 的渲染、按钮和最终登录脚本均可使用页面绑定的 `java.open`。通知回调继续使用原有 `java=source`，不借用其它前台 Activity。
- #34：交互回调/登录脚本支持 `java.open("rss", articleUrl, title, rssSourceUrl)`。目标订阅源须已导入，沿用该源的请求头、Cookie 和正文规则；空链接按订阅源的列表/单页模式处理。
- #36：封面控件裁剪完整 `draw`（包括背景和前景），网格外层卡片只裁剪一次，使用实际页面背景而非固定白色填充。
- #25：沿用现有 ResourcesLoader 色板实现，新增浅色输入和弹窗标题的对比度回归，不替换主题系统。

## 发现容器 MCP（#41 / #42）

沿用已有 MCP 服务、Bearer Token 鉴权。仅增加 MCP 工具，本次没有新增独立 HTTP API 或 Web 管理页面。

- `list_explore_containers`：可选 `search` / `enabled`。
- `add_explore_container`：`container` 为 JSON **字符串**。
- `add_explore_containers`：使用 `containers`（JSON 数组字符串）或 `sourceUrls`（直接字符串数组），二选一，单批最多 50 项。
- `update_explore_container`：按 id 修改配置。
- `delete_explore_container`：按 id 删除并清缓存。

### 单条：仅分类 URL 或默认首项

```json
{"container":"{\"sourceUrl\":\"https://source.example\",\"kindUrl\":\"https://source.example/hot\",\"style\":0,\"groupName\":\"推荐\"}"}
```

省略分类字段取首个有效分类。也可用 `kindTitle` 或成对的 `kindTitles` / `kindUrls`。

### 批量：每个书源一张全部分类卡

```json
{"sourceUrls":["https://source-a.example","https://source-b.example"],"groupName":"批量导入","style":0,"listCount":3}
```

默认保存当前全部分类；`dynamic: true` 保留运行时全部分类。分类标题/URL 包含持久化分隔符（例如 `<,>`）时使用动态分类，避免切坏 URL。

### 批量：每项独立配置

```json
{"containers":"[{\"sourceUrl\":\"https://source-a.example\",\"kindTitle\":\"热门\"},{\"sourceUrl\":\"https://source-b.example\",\"allKinds\":true}]"}
```

书源必须存在且启用发现，有有效分类。采用严格校验：无效输入返回错误，不静默跳过；全部准备完毕后一个数据库事务追加。忽略输入的 id / sortOrder，由 Room 分配 ID 和连续顺序，避免恢复导入式 REPLACE 覆盖已有卡片。

## 阅读记录迁移与备份兼容

数据库 93 → 94 使用手动迁移，历史 schema 93 不修改。

- 仅有唯一同名在架书时补充 URL/作者/封面。
- 同名歧义或已删除记录使用 `legacy:` + UTF-8 十六进制的稳定键；不凭空猜作者。
- 保留空 deviceId 的历史贡献，不假定属于当前设备。
- 备份按同设备同 URL 合并累计值（取较大值），不重复相加。
- 没有 URL 的旧备份优先复用已迁移身份，避免重新加书/换源后同一快照跨键重复统计；无法安全区分时记录恢复错误而不新增重复累计。
- 会话只提交不可变快照和耗时增量，数据库事务读取最新累计值，避免排队写入串书或覆盖。

## 验证命令

SQLite 实际生产 SQL 回归（无需 Android）：

```shell
python .github/scripts/check_read_records.py
```

Android 构建（Windows PowerShell，为避免资源竞争串行运行）：

```powershell
$env:KSP_INCREMENTAL='false'
.\gradlew.bat :app:assembleAppDebug :app:assembleAppDebugAndroidTest --no-daemon --max-workers=1
```

设备回归类：`MigrationTest`、`ReadRecordLayoutTest`、`CoverImageViewDrawingTest`、`LightThemeTextColorTest`、`RssCallbackLoadingTest`。后者用实际阅读 ViewModel 验证未缓存文章和目标源请求头，但不发送真实网络请求。迁移和像素/布局检查与真实网络源的完整交互验证不是同一回事；未执行的设备测试不能算作通过。

## 本机验证记录（2026-10-18）

- 聚焦 JVM：18 个测试类、85 项通过，0 失败/跳过（含 MCP、Session、备份合并/恢复、Rhino 登录/路由、封面/主题契约和旧版发现接线）。
- SQLite：生产迁移 SQL + DAO 查询校验通过；5 条历史记录、总耗时 250 保持不变，并与 Room 94 schema 完整列定义/主键/默认值/索引比较。
- 全量 JVM 曾运行到 691 项，22 项失败。已修复其中两个因数据库升级导致的版本号锁死断言；其余涉及 Windows 无 secp256k1 Android 原生库/未模拟 org.json，以及既有菜单/动效静态哨兵与现代码不一致，不能据此声称全量通过。相关生产文件和静态哨兵未在本次变更中修改。
- 部分构建尝试遇到本机原生内存不足及 Kotlin daemon 退出；与源码编译错误区分记录。模拟器与编译按顺序运行，避免同时争用内存。

## 仍需外部资料/设备确认

- #40 仅提供「明月书阁订阅源」名称，未提供可验证网站地址、旧订阅源 JSON 或规则；不能用同名网站猜测并发布来源不明的订阅源。
- #34 原问题的具体书源/订阅源通过其它渠道提供，仓库中没有可复现样本；真实登录态、目标源请求头和正文仍应由该样本验收。
- #25 / #36 应继续检查 Android 16 厂商机型的硬件圆角和日夜切换；软件 Canvas 回归不等于所有厂商渲染路径一致。
- Test Build 和 Release Build 保持仅 `workflow_dispatch` 手动触发。
