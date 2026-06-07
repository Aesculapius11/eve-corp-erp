# EVE Corp ERP 代码审计 — 修复 TODO

基于 2026-06-07 代码审计，按优先级排序。

---

## 🔴 CRITICAL

- [ ] **#1 Token 刷新未实现** — `TokenManager.kt:50`
  - 实现 POST `login.eveonline.com/v2/oauth/token`，grant_type=refresh_token
  - Interceptor 里调用后能自动重试 401 请求
  - 影响：不修则用户每 20 分钟需重新登录

- [ ] **#10 CorporationId 未获取** — `EsiAuthManager.kt` verifyToken 后
  - 登录后调用 `GET /characters/{id}/` 获取 corporation_id
  - 保存到 TokenManager
  - 影响：不修则 corpId=0，所有军团数据请求失败

- [ ] **#2 EsiAuthActivity 缺少失败处理** — `EsiAuthActivity.kt:28`
  - state 校验失败或 token 交换失败时显示错误提示
  - 不跳转 MainActivity
  - 影响：登录失败时用户无感知

- [ ] **#3 TokenManager key 硬编码为 "***"** — `TokenManager.kt:58-63`
  - 改为 `"access_token"` / `"refresh_token"`
  - 影响：维护混淆，功能不受影响

- [ ] **#4 分页拉取未限流** — 多个 Repository
  - 每页请求间加 `delay(100)` 或根据 ESI 错误限制头动态调整
  - 影响：军团数据超过 2 页时触发 ESI 429

---

## 🟠 MAJOR

- [ ] **#5 SyncWorker 串行同步** — `SyncWorker.kt:31-38`
  - 改为 `coroutineScope { async {} }` 并行执行
  - 影响：Worker 每次运行耗时过长

- [ ] **#6 DashboardViewModel.isRefreshing 未暴露** — `DashboardViewModel.kt:46`
  - 将 isRefreshing 合并到 DashboardUiState
  - 影响：UI 无法显示刷新状态

- [ ] **#7 HangarRepository division mapping 硬编码** — `HangarRepository.kt:89`
  - 根据 location_flag（CorpSAG1-7）映射到 divisionKey
  - 影响：不修则机库功能废了

- [ ] **#8 EsiAuthManager 异常被静默吞掉** — `EsiAuthManager.kt:76`
  - 至少记录日志，最好返回错误信息给 UI
  - 影响：登录失败时无法排查原因

- [ ] **#9 Repository 职责划分** — IndustryRepository vs IndustryJobRepository
  - 合并或明确边界
  - 影响：代码可维护性

---

## 🟡 MINOR

- [ ] **#11 WalletRepository.syncJournal 未做分页** — `WalletRepository.kt`
  - 添加分页循环拉取
  - 影响：流水超过 1000 条会丢失

- [ ] **#12 移除 Moshi KotlinJsonAdapterFactory** — `NetworkModule.kt`
  - 已用 KSP codegen，不需要反射 adapter
  - 影响：减小包体积

- [ ] **#13 常量分散** — HAAJINEN_SYSTEM_ID 重复定义
  - 提取到 Constants.kt
  - 影响：代码可维护性

- [ ] **#14 缺少单元测试**
  - Repository 层（Mock ESI API）
  - ViewModel 层（Mock Repository）
  - TokenManager（加密存储）
  - 影响：代码质量保障

---

## 进度跟踪

| 日期 | 完成项 | 备注 |
|------|--------|------|
| 2026-06-07 | #1 Token 刷新 | TokenRefresher + EsiAuthInterceptor 重写 |
| 2026-06-07 | #2 CorporationId 获取 | verifyToken 后调用 /characters/{id}/ |
| 2026-06-07 | #3 EsiAuthActivity 失败处理 | 错误提示 + 不跳转 |
| 2026-06-07 | #4 TokenManager key | 已是正确值 |
| 2026-06-07 | #5 分页拉取限流 | delay + 429 重试 |
| 2026-06-07 | #6 SyncWorker 并行 | coroutineScope + async |
| 2026-06-07 | #7 isRefreshing 暴露 | 合并到 DashboardUiState |
| 2026-06-07 | #8 Division mapping | location_flag → divisionKey |
| 2026-06-07 | #9 异常处理 | Log.e 记录日志 |
| 2026-06-07 | #10 Repository 划分 | IndustryJobRepository 合入 IndustryRepository |
| 2026-06-07 | #11 Journal 分页 | 分页循环拉取 + 429 重试 |
| 2026-06-07 | #12 移除反射 adapter | 删除 KotlinJsonAdapterFactory |
| 2026-06-07 | #13 常量提取 | Constants.kt |
| 2026-06-07 | #14 单元测试 | 6 个测试文件 |
