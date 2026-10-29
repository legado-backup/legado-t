# 提交规范

本项目使用 [Conventional Commits](https://www.conventionalcommits.org/) 规范提交信息，并通过 Commitizen 生成、Commitlint 校验。

## 快速提交

首次安装依赖（需要 Node.js 18 或更高版本）：

```bash
npm install
```

交互式创建提交：

```bash
npm run commit
```

提交时会自动运行本地 `commit-msg` hook。手动校验最近一次提交：

```bash
npm run commitlint
```

仓库已有部分历史提交不符合新格式，因此在这些提交上运行该命令可能会报告错误；它不会自动改写历史。新提交请使用 `npm run commit`，提交时的 hook 会阻止不符合规范的标题。

## 格式

```text
<type>(<scope>): <简短描述>
```

常用 `type`：

- `feat`：新增功能
- `fix`：修复问题
- `refactor`：重构，不改变外部行为
- `docs`：文档
- `test`：测试
- `ui`：界面调整（项目扩展类型）
- `build` / `ci` / `chore`：构建、持续集成或杂项

示例：

```text
fix(login): 保留登录 v2 开关状态 #58
feat(rss): 支持订阅源单对象导入
ci: 校验提交信息格式
```

描述可以使用中文；issue 或 PR 编号可以放在末尾，例如 `#58`。历史提交不会被自动改写，规范从新提交开始生效。

Pull Request 的 CI 只校验该 PR 相对目标分支新增的提交（`base.sha..head.sha`）。如果功能分支中保留了规范启用前的旧格式提交，需要在合并前压缩或改写这些提交；目标分支已有的历史提交不会因此被重新检查。
