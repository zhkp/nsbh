# NSBH 文档导航（当前代码版）

当前代码已完成 WebFlux + R2DBC 迁移，文档已按现状更新。

## 阅读顺序（推荐）
1. `A_项目整体架构说明.md`
2. `B_模块依赖图.md`
3. `C_核心逻辑地图.md`
4. `D_关键类说明.md`
5. `E_设计模式分析.md`
6. `F_复杂代码讲解_ConversationService.md`
7. `G_项目风险地图.md`
8. `H_技术说明书.md`
9. `I_配置速查表.md`
10. `J_聊天时序图.md`

## 按目标查阅
- 快速上手：`A` + `D`
- 排查链路：`C` + `F`
- 架构评审：`B` + `E` + `G` + `H`
- 扩展开发：`A` + `C` + `D` + `G`
- 配置排错：`I`
- 沟通流程：`J`

## 说明
- API：Spring WebFlux（`Mono/Flux`）
- 数据：Spring Data R2DBC（H2 默认，可切 Postgres）
- LLM：`mock|openai` 可切换
- 工具：`@NsbhTool` + `ToolRegistry` 自动发现
