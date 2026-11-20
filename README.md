# 碳排放披露数据血缘与版本影响管理平台

面向制造集团的碳排放披露管理平台：按工厂、能源类型、产品线汇总范围一/二/三排放，
对每一项 tCO2e 指标回答"用了哪份表计数据、哪版排放因子、为什么没随最新资料改变"。

- **来源批次**：工厂上传燃料/用电/采购活动数据，审核员核验；采购批次按来源行+供应商凭证检测冲突，已核算采购量不可被后续上传覆盖
- **因子目录**：按地域、适用期维护排放因子版本，审批生效；平台只使用地域与期间匹配的批准因子，缺因子时指标列为**待核**，不用集团平均数兜底
- **核算运行**：启动时快照因子清单（运行中替换因子不影响本次运行），活动量×因子生成带血缘的排放结果
- **披露模块**：运行结果冻结为报告版本；产品线合计与工厂明细不勾稽时报告保持**待核**，差额不能归零；已发布版本内容不可改写
- **影响服务**：来源补录/修订/缺失、因子修订时自动重算并对比受影响指标与已发布报告，生成重述建议与差异说明，经审批后以新版本重述，披露历史完整保留
- **责任归属（运营控制边界）**：以不可变的收购、剥离、设备移交、停计量、租赁、裁定、追溯更正事件保存证据，按报告截止点复演事件流推导当时的控制状态；用责任区间交集把活动量切分为**纳入/排除/待裁定**片段。控制权与设备交接不一致的时段只能待裁定、不并入任一集团趋势；相反法律证据不能被多数票覆盖；冻结后补入的活动或裁定只生成新的比较版本并保留原范围

## 技术栈与架构

```
┌────────────┐   HTTP    ┌─────────────────────────┐      ┌──────────────┐
│ Vue3 前端   │ ───────▶ │ SpringBoot 后端 (Java)   │ ───▶ │ PostgreSQL   │
│ 血缘下钻/   │  /api/*  │ 批次校验·运行编排·版本状态 │      │ 来源/因子/运行 │
│ 影响对比/   │          │ 影响评估·披露审批          │      │ 快照/披露证据 │
│ 披露审批    │          └─────────────────────────┘      └──────────────┘
└────────────┘
```

- 前端：Vue 3 + Vite + Element Plus + Vue Router（hash 模式）
- 后端：Spring Boot 3.5（Web / Data JPA / Validation），HMAC 令牌认证，角色权限（管理员/分析员/审核员/财务披露）
- 数据库：PostgreSQL（表结构见 `backend/src/main/resources/schema.sql`）

## 一、Docker Compose 一键启动（推荐）

```bash
docker compose up --build
```

- 前端：http://localhost （nginx，反向代理 /api 到后端）
- 后端：http://localhost:8080/api/health
- 数据库：PostgreSQL 18（compose 内部 `postgres:5432`，库/用户/密码均为 `ccdev`）

首次启动时后端自动建表并写入样例数据（工厂含 2025-07 年中收购的武汉工厂、责任主体与不可变责任事件、表计/燃料/采购批次、因子、2025-05 已发布报告、2025-07 待裁定报告）。

## 二、本地开发启动

前置：本机 PostgreSQL 可用（`127.0.0.1:5432`，库/用户/密码 `ccdev`）。

```bash
# 1. 后端（端口 8080；启动时自动建表+写入种子数据）
cd backend
mvn package -DskipTests
java -jar target/carbon-platform.jar

# 2. 前端（二选一）
#  a) 开发模式：vite 开发服务器，端口 5173，代理 /api 到 8080
cd frontend && npm install && npm run dev        # 打开 http://localhost:5173
#  b) 一体化模式：构建后由后端直接托管
cd frontend && npm install && npm run build      # 然后打开 http://localhost:8080
```

> 后端以 `file:./frontend/dist/` 与 `file:../frontend/dist/` 作为可选静态资源目录，
> 在仓库根目录或 backend/ 目录启动 jar 均可托管已构建的前端。

## 三、测试账号

| 账号 | 密码 | 角色 | 主要权限 |
| --- | --- | --- | --- |
| `admin` | `admin123` | 管理员 | 全部操作 |
| `analyst` | `analyst123` | 分析员 | 上传/修订批次、新建因子版本、启动运行、创建报告 |
| `auditor` | `auditor123` | 审核员 | 核验/驳回/作废批次、批准因子版本 |
| `finance` | `finance123` | 财务披露 | 审批/发布报告、接受/驳回重述建议 |

越权操作会被拒绝（403），例如分析员不能核验批次。

## 四、验证方式

### 4.1 自动化验收回放（已实际执行，共 112 项全部通过）

```bash
# 前置：后端已启动且数据库为初始种子状态
python3 scripts/verify.py             # S0~S5 基线场景，59 项断言
python3 scripts/verify_ownership.py   # S6~S8 责任归属场景，53 项断言
# 如需重置到种子状态后重跑：
bash scripts/reset-db.sh              # 清空数据库
# 重启后端（自动重建表+种子数据），再依次执行两个回放脚本
```

回放场景（对应验收要求）：

| 场景 | 内容 | 关键断言 |
| --- | --- | --- |
| S0 | 种子基线 | 2025-05 已发布报告总量 2503.18 tCO2e（范围一/二/三 = 70.92/917.26/1515）；运行绑定 6 个因子快照；血缘可下钻到批次行+因子版本 |
| S1 | 来源补录 | 补录柴油 1500L 核验后自动生成影响评估（苏州/精密结构件/SCOPE1 +4.02）与重述建议；接受→冻结 v2→审批→发布；v1 标记"已重述"，历史保留 |
| S2 | 因子修订 | 运行启动后批准新版电力因子（0.5566→0.5420）：本次运行仍用 v1（绑定快照），新运行用 v2；自动生成对 2025-05 已发布报告的影响评估（苏州 SCOPE2 合计 -16.06）与重述 |
| S3 | 并发采购更正 | 修订试图把已核算凭证 V-1001 采购量 500→520：核验被拒（409）并标记冲突，原数量未被覆盖；合规修订（不动已消耗行+新增凭证行）通过；两工厂同时修订同一批次，第二份被拒（409） |
| S4 | 勾稽不平 | 含未分摊公共用电（54.2 tCO2e）与缺因子待核指标的报告保持待核（RECONCILE_FAILED），审批/发布均被拒（409）；修复分摊+批准因子后重算、冻结、审批、发布 |
| S5 | 已发布报告发现来源缺失 | 作废已消耗的 2025-05 燃料批次：自动生成影响评估（苏州 SCOPE1 -70.92）与重述建议；已发布 v3 内容不被覆盖；接受重述→v4 发布，v1~v3 均为"已重述"，披露历史完整 |
| S6 | 年中收购 + 共享租赁设备 | 武汉工厂 2025-07：股权交割 07-10、出租方停计量 07-16、设备交接 07-21。设备活动按天切为 1-9 排除 / 10-20 待裁定 / 21-31 纳入；活动量切分守恒；待裁定结果无排放不进合计；报告勾稽状态 PENDING_OWNERSHIP，审批/发布被拒；输出待裁定缺口、年中收购、共享租赁、排除四类可比性注释；越权登记被 403 拒绝 |
| S7 | 责任裁定与冻结后比较版本 | 审核员裁定空档期纳入→自动生成 OWNERSHIP_RULING 影响评估与候选运行；新运行待裁定消失；基于候选运行生成 POST_FREEZE_COMPARISON 比较版本，原 v1 责任范围（待裁定值与截止点）保留不被改写；比较版本重新审批发布后待裁定归零 |
| S8 | 冲突证据 + 追溯更正 + 历史复演 | 剥离 08-05 与对立收购 08-20 显式冲突：08-05~08-20 只能待裁定，再补一份收购证据（多数票）也不能覆盖；CORRECTION 事件追溯更正剥离证据后原事件标记 RULED 但保留、冲突解除、整月纳入；更正前的历史运行按原截止点复演，待裁定活动量 160000 不变，不按当前股权重挂历史 |

### 4.2 页面手动验证

登录 http://localhost:8080 （或 5173）后：

1. **仪表盘**：最新发布披露总量、范围一/二/三、待处理影响评估数、待核报告/指标数、分工厂排放
2. **核算运行 → 详情 → 血缘**：任一排放结果可下钻到来源批次行（含供应商凭证、核验人）与因子版本（值、适用期、批准人）
3. **影响评估 → 详情**：受影响指标旧值/新值/差异对比、受影响报告链接、重述建议处置
4. **披露报告 → 详情**：版本历史（已发布/已重述/被取代）、披露条目、勾稽核对表、重述记录
5. **来源批次**：上传/核验/修订/作废；修订已消耗采购批次时可见 409 冲突提示；上传时可按行指定共享/租赁设备编号
6. **因子目录**：新增版本→批准（替换旧版）；使用追踪展示绑定该版本的运行
7. **责任归属**：集团合并口径卡片（纳入/排除/待裁定 tCO2e 与可比性注释）；不可变责任事件登记（收购/剥离/交接/停计量/租赁/更正）；责任裁定登记；按截止点实时推导责任片段预览
8. **核算运行 → 详情**：新增"活动责任归属明细"，展示每条来源行按责任片段切分后的活动量、归属分类与依据
9. **披露报告 → 详情**：版本列表显示责任口径（初始冻结/冻结后比较版本）与待裁定数；版本详情含纳入/排除/待裁定汇总、推导截止点与可比性注释；可基于候选运行生成比较版本

## 五、关键业务规则实现

- **因子绑定快照**：运行创建时把当时已批准且覆盖该期间的因子版本写入 `run_factor_bindings`，核算只读快照；因子在运行中被替换只影响之后的新运行（部分唯一索引保证同一期间同时只有一个进行中的正式运行）
- **待核而非平均数**：因子匹配严格按 工厂地域+能源类型+适用期间；匹配不到批准因子时结果标记 `PENDING_FACTOR`（排放量留空），报告勾稽状态为 `PENDING_FACTOR`，审批/发布被拒
- **采购量保护**：修订批次核验时，原批次中已被核算消耗（存在血缘记录）的采购行必须按凭证号保持数量不变，否则整单拒绝并标记冲突；同一批次同时只允许一个进行中的修订
- **勾稽**：冻结时按"工厂明细合计 vs 产品线合计"核对，未分摊差异记入 `reconciliations`；`MISMATCH`/`PENDING_FACTOR` 状态下审批与发布均被拒，无"差额归零"入口
- **不可改写与重述**：已发布版本内容不更新；来源缺失/因子修订触发影响评估（候选运行 vs 已发布版本逐指标对比），重述建议被接受后冻结新版本，发布时旧版本标记 `RESTATED`，版本链完整可查
- **责任归属事件不可变 + 截止点推导**：系统保存不可变的收购/剥离/移交/停计量/租赁/裁定/更正事件（`ownership_events`），不预先物化"唯一正确"区间；核算运行创建时固定 `ownership_cutoff`，运行与冻结均按该截止点复演事件流，运行期间新增事件不影响本次运行，历史运行可按各自截止点复演
- **待裁定而非主观归并**：法人控制权（LEGAL）与设备交接/计量（HANDOVER/METER_STOP/LEASE）不一致的时段、显式标记的相反法律证据冲突区间，一律 `PENDING`，不并入任一集团趋势；补充证据的"多数票"不能覆盖冲突，只能由审核员裁定或追溯更正解除
- **活动量按天切分且守恒**：来源行活动量按责任片段与活动发生期间的天数交集比例切分，末段吸收舍入差，片段之和等于原始活动量；仅 INCLUDED 片段进入合并核算与披露条目，EXCLUDED/PENDING 片段单独留存 pro forma 参照值
- **冻结后比较版本**：冻结/发布之后补入的活动或责任裁定不改写已固化版本；经候选运行生成 `POST_FREEZE_COMPARISON` 比较版本（`comparison_basis` 标记），保留原范围与推导截止点，须重新审批发布；待裁定版本在 `PENDING_OWNERSHIP` 状态下审批/发布被拒

## 六、API 概览（/api，除登录外均需 `X-Auth-Token`）

| 模块 | 端点 |
| --- | --- |
| 认证 | `POST /auth/login`、`GET /auth/me`、`GET /health` |
| 元数据 | `GET /meta/plants`、`GET /meta/product-lines`、`GET /meta/energy-types` |
| 批次 | `GET/POST /batches`、`GET /batches/{id}`、`POST /batches/{id}/verify|reject|revise|void` |
| 因子 | `GET/POST /factors`、`POST /factors/{id}/versions`、`POST /factors/versions/{vid}/approve|retire`、`GET /factors/versions/{vid}/usage` |
| 运行 | `GET/POST /runs`、`GET /runs/{id}`、`GET /results/{rid}/lineage` |
| 披露 | `GET/POST /reports`、`GET /reports/{id}`、`POST /reports/{id}/freeze|approve|publish`、`GET /reports/versions/{vid}` |
| 影响 | `GET /impacts`、`GET /impacts/{id}`、`POST /impacts/restatements/{id}/accept|reject` |
| 责任归属 | `GET/POST /ownership/parties`、`GET/POST /ownership/events`、`GET/POST /ownership/rulings`、`GET /ownership/segments`、`GET /ownership/consolidated`、`GET /ownership/runs/{runId}/allocations`、`GET /ownership/versions/{versionId}` |
| 比较版本 | `POST /reports/{id}/comparison-versions` |
| 仪表盘 | `GET /dashboard/summary` |

## 七、目录结构

```
├── backend/                 # SpringBoot 后端
│   ├── Dockerfile
│   ├── src/main/resources/schema.sql       # 表结构（幂等，含责任事件/裁定/归属明细/版本汇总/注释）
│   └── src/main/java/com/group/carbon/     # config/security/model/repo/service/controller
│       ├── model/Ownership*.java、ActivityAllocation、ReportOwnershipSummary、ComparabilityNote
│       └── service/OwnershipService.java   # 不可变事件流 + 截止点推导 + 区间切分引擎
├── frontend/                # Vue3 前端
│   ├── Dockerfile  nginx.conf
│   └── src/views/           # 仪表盘/来源/因子/运行/影响/责任归属/披露页面
├── scripts/
│   ├── verify.py             # 基线验收回放（S0~S5，59 项断言）
│   ├── verify_ownership.py   # 责任归属验收回放（S6~S8，53 项断言）
│   └── reset-db.sh          # 重置本地数据库
└── docker-compose.yml       # 一键启动（postgres + backend + frontend）
```

## 八、验证记录（实际执行过的验证）

以下验证均在本开发容器内实际执行通过：

- `mvn clean package -DskipTests`：后端编译打包通过（Spring Boot 3.5.7，Java 25 运行时，编译目标 21）
- `npm install && npm run build`：前端构建通过（vite 6，产物 dist/）
- 后端启动后自动建表+种子数据：`/api/health` → UP；种子报告 2025-05 总量 2503.18 tCO2e 与手工核算一致；种子 2025-07 武汉报告因待裁定片段保持 `RECONCILE_FAILED`
- `python3 scripts/verify.py`：**59 项断言全部通过**（S0~S5 全场景回放，责任归属改造前后基线数值零变化）
- `python3 scripts/verify_ownership.py`：**53 项断言全部通过**（S6 年中收购+共享租赁、S7 裁定+冻结后比较版本、S8 冲突证据+追溯更正+历史复演，含角色权限 403 校验）
- 后端托管前端：`GET /` 与 `GET /assets/*.js` 均 200
- Docker Compose 配置：本容器无 docker CLI，`docker compose up` 未实际执行；Dockerfile/compose 已经静态核验（镜像标签、构建上下文、健康检查、依赖顺序、nginx 代理路径），请在有 Docker 的环境执行 `docker compose up --build` 验证
