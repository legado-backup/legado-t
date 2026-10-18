# 阅读[API](/app/src/main/java/io/legado/app/api/controller)

## 对于[Web](/app/src/main/java/io/legado/app/web/)的配置

您需要先在设置中启用"Web 服务"。

## 使用

### Web

以下说明假设您的操作在本机进行，且开放端口为1234。  
如果您要从远程计算机访问[阅读]()，请将`127.0.0.1`替换成手机IP。

#### 插入单个书源

请求BODY内容为`JSON`字符串，  
格式参考[这个文件](/app/src/main/java/io/legado/app/data/entities/BookSource.kt)

```
URL = http://127.0.0.1:1234/saveBookSource
Method = POST
```

#### 插入多个书源or订阅源

请求BODY内容为`JSON`字符串，  
格式参考[这个文件](/app/src/main/java/io/legado/app/data/entities/BookSource.kt)，**为数组格式**。

```
URL = http://127.0.0.1:1234/saveBookSources
URL = http://127.0.0.1:1234/saveRssSources
Method = POST
```

#### 插入JS单文件书源

请求BODY为JS源脚本原文。
App 侧执行与导入相同的校验与元数据提取，返回落库后的完整书源；
同 URL 覆盖时保留分组、启用开关等用户态字段，`lastUpdateTime` 以脚本内声明为准。

```
URL = http://127.0.0.1:1234/saveJsSource
Method = POST
```

#### 获取书源

```
URL = http://127.0.0.1:1234/getBookSource?url=xxx
URL = http://127.0.0.1:1234/getRssSource?url=xxx
Method = GET
```

#### 获取所有书源or订阅源

```
URL = http://127.0.0.1:1234/getBookSources
URL = http://127.0.0.1:1234/getRssSources
Method = GET
```

#### 删除多个书源or订阅源

请求BODY内容为`JSON`字符串，  
格式参考[这个文件](/app/src/main/java/io/legado/app/data/entities/BookSource.kt)，**为数组格式**。

```
URL = http://127.0.0.1:1234/deleteBookSources
URL = http://127.0.0.1:1234/deleteRssSources
Method = POST
```

#### 调试源

key为书源搜索关键词，tag为源链接

```
URL = ws://127.0.0.1:1235/bookSourceDebug
URL = ws://127.0.0.1:1235/rssSourceDebug
Message = { key: [String], tag: [String] }
```

#### HTTP请求日志

获取APP端HTTP请求日志。需在设置中开启"记录HTTP日志"；内存保留最新50条，请求/响应正文在记录时截断至4096字符。
列表返回`{ recording: [Boolean], logs: [摘要数组] }`，`recording`为开关当前状态；详情按`id`返回完整记录（含请求/响应头体）。

```
URL = http://127.0.0.1:1234/getHttpLogs?limit=50
URL = http://127.0.0.1:1234/getHttpLog?id=1
Method = GET
```

##### 开关记录

远程开关「记录HTTP日志」,与设置页开关状态同步;切换不清空已有记录。

```
URL = http://127.0.0.1:1234/setHttpLogRecording
Method = POST
Body = { enabled: [Boolean] }
```

#### 获取替换规则

```
URL = http://127.0.0.1:1234/getReplaceRules
Method = GET
```

#### 替换规则管理

请求BODY内容为`JSON`字符串，  
替换规则参考[这个文件](/app/src/main/java/io/legado/app/data/entities/ReplaceRule.kt)。

##### 删除

```
URL = http://127.0.0.1:1234/deleteReplaceRule
Method = POST
Body = [ReplaceRule]
```

##### 插入

```
URL = http://127.0.0.1:1234/saveReplaceRule
Method = POST
Body = [ReplaceRule]
```

##### 测试

返回测试文本text替换结果

```
URL = http://127.0.0.1:1234/testReplaceRule
Method = POST
Body = { rule: [ReplaceRule], text: [String] }
```

#### 搜索在线书籍

若想获取对应的书籍的目录正文 请先**插入书籍**以启用缓存，如果试读后决定不添加到书籍，请**删除书籍**

```
URL = ws://127.0.0.1:1235/searchBook
Message = { key: [String] }
```

#### 插入书籍

请求BODY内容为`JSON`字符串，  
格式参考[这个文件](/app/src/main/java/io/legado/app/data/entities/Book.kt)。

```
URL = http://127.0.0.1:1234/saveBook
Method = POST
```

#### 删除书籍

```
URL = http://127.0.0.1:1234/deleteBook
Method = POST
```

#### 获取所有书籍

```
URL = http://127.0.0.1:1234/getBookshelf
Method = GET
```

获取APP内的所有书籍。

#### 获取书籍章节列表

```
URL = http://127.0.0.1:1234/getChapterList?url=xxx
Method = GET
```

获取指定图书的章节列表。

#### 获取书籍内容

```
URL = http://127.0.0.1:1234/getBookContent?url=xxx&index=1
Method = GET
```

获取指定图书的第`index`章节的文本内容。

#### 获取封面

```
URL = http://127.0.0.1:1234/cover?path=xxxxx
Method = GET
```

#### 获取正文图片

```
URL = http://127.0.0.1:1234/image?url=${bookUrl}&path=${picUrl}&width=${width}
Method = GET
```

#### 保存书籍进度

请求BODY内容为`JSON`字符串，  
格式参考[这个文件](/app/src/main/java/io/legado/app/data/entities/BookProgress.kt)。

```
URL = http://127.0.0.1:1234/saveBookProgress
Method = POST
```

### [Content Provider](/app/src/main/java/io/legado/app/api/ReaderProvider.kt)

- 需声明`io.legado.READ_WRITE`权限
- `providerHost`为`包名.readerProvider`, 如`io.legado.app.release.readerProvider`,不同包的地址不同,防止冲突安装失败
- 以下出现的`providerHost`请自行替换

#### 插入单个书源or订阅源

创建`Key="json"`的`ContentValues`，内容为`JSON`字符串，  
格式参考[这个文件](/app/src/main/java/io/legado/app/data/entities/BookSource.kt)

```
URL = content://providerHost/bookSource/insert
URL = content://providerHost/rssSource/insert
Method = insert
```

#### 插入多个书源or订阅源

创建`Key="json"`的`ContentValues`，内容为`JSON`字符串，  
格式参考[这个文件](/app/src/main/java/io/legado/app/data/entities/BookSource.kt)，**为数组格式**。

```
URL = content://providerHost/bookSources/insert
URL = content://providerHost/rssSources/insert
Method = insert
```

#### 获取书源or订阅源

获取指定URL对应的书源信息。  
用`Cursor.getString(0)`取出返回结果。

```
URL = content://providerHost/bookSource/query?url=xxx
URL = content://providerHost/rssSource/query?url=xxx
Method = query
```

#### 获取所有书源or订阅源

获取APP内的所有订阅源。  
用`Cursor.getString(0)`取出返回结果。

```
URL = content://providerHost/bookSources/query
URL = content://providerHost/rssSources/query
Method = query
```

#### 删除多个书源or订阅源

创建`Key="json"`的`ContentValues`，内容为`JSON`字符串，  
格式参考[这个文件](/app/src/main/java/io/legado/app/data/entities/BookSource.kt)，**为数组格式**。

```
URL = content://providerHost/bookSources/delete
URL = content://providerHost/rssSources/delete
Method = delete
```

#### 插入书籍

创建`Key="json"`的`ContentValues`，内容为`JSON`字符串，  
格式参考[这个文件](/app/src/main/java/io/legado/app/data/entities/Book.kt)。

```
URL = content://providerHost/book/insert
Method = insert
```

#### 获取所有书籍

获取APP内的所有书籍。  
用`Cursor.getString(0)`取出返回结果。

```
URL = content://providerHost/books/query
Method = query
```

#### 获取书籍章节列表

获取指定图书的章节列表。  
用`Cursor.getString(0)`取出返回结果。

```
URL = content://providerHost/book/chapter/query?url=xxx
Method = query
```

#### 获取书籍内容

获取指定图书的第`index`章节的文本内容。  
用`Cursor.getString(0)`取出返回结果。

```
URL = content://providerHost/book/content/query?url=xxx&index=1
Method = query
```

#### 获取封面

```
URL = content://providerHost/book/cover/query?path=xxxx
Method = query
```

## MCP 服务

App 内直出 MCP server(Streamable HTTP,端点 `/mcp`,端口默认 1236,可在设置修改)。设置页开启「MCP 服务」后,PC 侧一条命令注册:

```
claude mcp add legado --scope user --transport http --header "Authorization: Bearer <Token>" http://<手机IP>:1236/mcp
```

`<Token>` 在设置-其它-MCP Token 查看/复制(点击即复制),可随时重新生成(立即生效,无需重启)。首次开启服务时自动生成;所有 `/mcp` 请求必须携带该 token,否则 401。

当前注册工具 29 个:

- 书源:save_source / debug_source / list_sources / get_source / delete_sources / check_source
- 日志:get_http_logs / get_http_log / set_http_log_recording / get_app_logs
- 运行时:eval_js / get_cookies / set_cookie / clear_cookies
- 定时任务:list_auto_tasks / get_auto_task / save_auto_task / delete_auto_tasks / run_auto_task
- 朗读引擎:list_tts / get_tts / save_tts / delete_tts / test_tts
- 发现容器:list_explore_containers / add_explore_container / add_explore_containers / update_explore_container / delete_explore_container

与「Web 服务」相互独立;工具直调 App 内部,Web 服务关闭时全部可用。本次发现容器管理复用 `/mcp` 工具调用,没有新增独立的 HTTP 或 WebSocket 端点。

### 发现容器工具

这 5 个工具管理发现页的容器卡片配置,不抓取发现书籍。新增、批量新增和更新均要求 `sourceUrl` 对应 App 内已有、已启用发现且有有效发现分类的书源。

#### 容器配置

`add_explore_container` / `update_explore_container` 的 `container` 是 **JSON 对象字符串**,不是直接嵌套的对象。配置字段如下:

- `sourceUrl`:必填字符串,即书源的 `bookSourceUrl`;`sourceName` 会按当前书源名回填。
- 分类:`kindTitle` / `kindUrl` 指定一个分类;`kindTitles` / `kindUrls` 是以逗号分隔、顺序对应的多分类字符串,不是数组。`kindUrls` 可省略,但不能只传它而不传 `kindTitles`。分类按当前书源匹配,保留的标题及 URL 快照可用于兜底;单条新增或 `containers` 中的单项未指定分类时取首个有效分类。
- `allKinds`:可选布尔值,`true` 保存当前全部有效分类快照;`dynamic`:可选布尔值,`true` 在运行时展示书源全部有效分类。两者不能同时为 `true`,使用任一全部分类模式时不得同时指定上述四个分类字段。所选分类名或 URL 含中英文逗号/分号时,会自动改用动态全部分类,避免快照被错误拆分。
- `customTitle`:可选字符串或 `null`,空白时显示分类名;`groupName`:可选分组字符串,默认空串。
- `style`:可选整数,`0` 横滑（默认）/ `1` 列表;`listCount`:可选整数 `1`～`20`,默认 `3`,仅列表样式使用;`enabled`:可选布尔值,默认 `true`。
- `id` / `sortOrder`:新增时忽略传入值,由数据库自增分配 ID,在同一追加事务内分配排序并按输入顺序连续追加到末尾,不会按传入 ID 覆盖旧卡片。更新时 `id` 必须是已有的正整数,`sortOrder` 是要保留或修改的排序值。

以下示例均为 MCP `tools/call` 的 `params`,不是新 HTTP 接口的请求体。书源 URL、分类和容器 ID 请替换为 App 中的实际值。

#### list_explore_containers

可选参数 `search`（字符串,不区分大小写的摘要子串过滤）、`enabled`（布尔值,缺省列全部）。返回按排序排列的容器摘要,含 `id`、标题、书源、分类快照、样式、数量、启用状态、分组和 `sortOrder`。

```json
{"name":"list_explore_containers","arguments":{"search":"热门","enabled":true}}
```

#### add_explore_container

必填参数 `container`（JSON 对象字符串）,新增一个卡片。新增不做跨调用幂等,重复调用会再建一张卡片。

```json
{"name":"add_explore_container","arguments":{"container":"{\"sourceUrl\":\"https://example.com\",\"kindTitle\":\"热门\",\"style\":1,\"listCount\":8,\"groupName\":\"榜单\"}"}}
```

#### add_explore_containers

`containers` 与 `sourceUrls` **必须且只能提供一个**:

- `containers`:**JSON 数组字符串**,每项同单条容器配置。每项单独设置标题/样式/数量/启用/分组;顶层 `allKinds` / `dynamic` 可统一整批分类模式,不能与单项模式或分类选择冲突。
- `sourceUrls`:**原生 JSON 字符串数组（native 数组）**,不是数组字符串。默认每源创建一张当前全部分类快照卡,`dynamic:true` 改为动态全部分类;可在顶层共用 `groupName`、`customTitle`、`style`、`listCount`、`enabled`。
- 每批必须有 `1`～`50` 个输入项,上限在去重前检查。**仅 `sourceUrls` 在本次请求内对去除首尾空白后的 URL 去重**,保留首次出现的顺序;`containers` 不去重,也不会对照已有卡片或其他调用去重。两种模式均不做跨调用幂等,重试已成功的新增会产生新卡片。
- 批量新增为 **strict atomic（严格、原子）**:书源不存在、未启用发现、无有效分类或参数/分类配置不合法等均报错,不静默跳过。先校验全部输入,再在同一事务内分配 ID/排序并整批写入,任一项无效或写入失败均整批不写入。

按配置批量新增（注意 `containers` 内的转义）:

```json
{"name":"add_explore_containers","arguments":{"containers":"[{\"sourceUrl\":\"https://example.com\",\"kindTitle\":\"热门\",\"groupName\":\"榜单\"},{\"sourceUrl\":\"https://example.org\",\"allKinds\":true}]"}}
```

按书源批量新增动态全部分类卡:

```json
{"name":"add_explore_containers","arguments":{"sourceUrls":["https://example.com","https://example.org"],"dynamic":true,"groupName":"榜单","style":1,"listCount":8,"enabled":true}}
```

#### update_explore_container

必填参数 `container`（含已有正整数 `id` 的**完整配置 JSON 对象字符串**）。这是整条配置替换,不是局部 patch;漏传字段按默认值处理,不会自动保留原值。应先用 `list_explore_containers` 取回当前配置字段,修改后完整传入;摘要中的 `title` / `styleName` 无需传入。保留动态全部分类模式时需传 `dynamic:true` 并省略四个分类字段。

仅在书源目标或分类指向/集合变化（`sourceUrl`、`kindTitle`、`kindUrl`、`kindTitles`、`kindUrls`）时清理该卡片的书籍缓存;只改标题、样式、数量、分组、启用或排序不会主动清缓存。

```json
{"name":"update_explore_container","arguments":{"container":"{\"id\":42,\"sourceUrl\":\"https://example.com\",\"sourceName\":\"示例书源\",\"kindTitle\":\"热门\",\"kindUrl\":\"https://example.com/hot\",\"kindTitles\":\"热门\",\"kindUrls\":\"https://example.com/hot\",\"customTitle\":\"本周热门\",\"style\":1,\"listCount\":8,\"sortOrder\":7,\"enabled\":true,\"groupName\":\"榜单\"}"}}
```

#### delete_explore_container

必填参数是 `ids`（原生 JSON 整数数组）,即使只删一个也传数组,不是 `id` 或 `container`。删除存在的卡片并清理其书籍缓存;部分 ID 不存在时会在结果中提示,全部不存在时报错。

```json
{"name":"delete_explore_container","arguments":{"ids":[42,43]}}
```
