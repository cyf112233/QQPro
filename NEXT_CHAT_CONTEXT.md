# 下一次对话上下文

## 当前目标
给 QQPro 增加“防撤回”功能，并把设置入口独立到菜单里的 `高级功能（by cxkcxkckx）` 分区下。

## 已完成的代码改动
- `app/src/main/java/momoi/mod/qqpro/Settings.kt`
  - 新增 `Settings.antiRecall` 开关，默认 `true`。
- `app/src/main/java/momoi/mod/qqpro/hook/设置页.kt`
  - 新增分区标题：`高级功能（by cxkcxkckx）`
  - 在该分区下加入 `防撤回` 开关，绑定 `Settings.antiRecall`
- `app/src/main/java/byd/cxkcxkckx/AntiRecall.kt`
  - 新建独立包 `byd.cxkcxkckx`
  - `@Mixin class AntiRecall : MsgServiceImpl()`
  - 覆写 `deleteRecallMsg(contact: Contact?, msgId: Long, callback: IOperateCallback?)`
  - 覆写 `deleteRecallMsgForLocal(contact: Contact?, msgId: Long, callback: IOperateCallback?)`
  - 当 `Settings.antiRecall.value == true` 时直接返回成功回调，阻止撤回

## 关键确认
- `MsgServiceImpl` 的 Kotlin/Java 签名里，`Contact` 参数是可空的，所以覆写必须写成 `Contact?`。
- 原始方法调用用 `super.deleteRecallMsg(...)` / `super.deleteRecallMsgForLocal(...)`。
- 用户明确要求：
  - 代码/包名使用 `byd.cxkcxkckx`
  - 防撤回单独放到菜单分区 `高级功能（by cxkcxkckx）`
  - 暂时不必继续编译测试，由用户后续处理

## 目前仓库状态
- 工作区里已经有上述三个文件的修改/新增。
- `.gitignore` 也有过变动，但用户后来明确说“别管 gitignore 了”。

## 重要文件位置
- `app/src/main/java/momoi/mod/qqpro/Settings.kt`
- `app/src/main/java/momoi/mod/qqpro/hook/设置页.kt`
- `app/src/main/java/byd/cxkcxkckx/AntiRecall.kt`

## 后续如果继续做
1. 先检查 `AntiRecall.kt` 在目标环境里的实际编译情况。
2. 若有编译问题，再微调 `MsgServiceImpl` 的具体覆写签名。
3. 如果需要更稳，可再补一层消息列表刷新兜底，但目前先按接口拦截方案走。

## 可直接复制给下一次对话的简述
> 已经把防撤回做成独立包 `byd.cxkcxkckx`，设置页新增了 `高级功能（by cxkcxkckx）` 分区和 `防撤回` 开关，核心逻辑在 `AntiRecall.kt` 里拦截 `deleteRecallMsg` / `deleteRecallMsgForLocal`。下一步如果继续，就先看编译是否需要微调签名。