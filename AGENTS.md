1. 你可以通过 @README.md 快速了解项目，涉及项目级别变更和重大变化时及时同步并维护此文档。
2. Git 提交信息格式：`<类型>([<范围>]): <中文改动说明>`。英文前缀（如 `feat`、`fix`、`refactor`、`docs`、`style`、`chore`、`perf`、`test`）表示改动大类，范围可选，中文部分写明改动内容和原因，避免过于简略。示例：`feat(api): 新增用户查询接口，支持按邮箱模糊搜索`。
3. 涉及 UI、交互变更时，务必先读取并同步维护 @DESIGN.md。该文件作为当前 UI/交互设计规范，设计样式时必须优先使用 @DESIGN.md 中注明的公共样式 token。
4. 遵守 TDD / RISC /奥卡姆原则完成功能开发与问题修复。请不要交付无法通过编译或有明显问题的代码，修改完成后Debug打包。

## 统一持久化约束

1. 所有需要跨进程、重启或页面重建保存的业务状态，必须使用 `app/src/main/java/com/github/gbandszxc/goodtvplorer/data/persistence/` 中的 Room / SQLite 公共存储层。
2. 新增持久化功能时，必须同时补齐 Entity、DAO、Repository、测试、`docs/architecture.md` 的“统一持久化”章节；必要时更新 README。
3. UI、Activity、ViewModel、domain 层不得直接访问 Room DAO、SQLite、DataStore、SharedPreferences 或自行读写配置文件；只能调用 Repository。
4. 不得新增 DataStore、SharedPreferences 或临时文件配置作为业务状态存储。缓存文件仍可使用 `cacheDir`，但不得承担配置、连接或导航恢复职责。
5. 删除具有来源标识的业务实体时，必须在同一事务中清理其关联的持久化状态。
6. SMB 密码必须以 Android Keystore 保护的密文保存到 SQLite，加解密只能在公共持久化层完成；不得打印、记录或在错误信息中暴露密码，密文格式演进必须提供 Room 迁移并保持 Repository 接口稳定。

## 交付要求

- 修改持久化结构后必须运行 Debug 构建、单元测试和相关 instrumentation 测试。
- 每个独立逻辑改动单独提交，提交信息使用规范前缀与中文说明。
