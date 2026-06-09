# Code Review Fixes

## CRITICAL
- [x] 1. Room 数据库加 fallbackToDestructiveMigration
- [x] 2. TokenRefresher 用 Mutex 替代 runBlocking

## MAJOR
- [x] 3. SyncWorker 并行任务隔离，单个失败不取消其余
- [x] 4. MarketRepository.toEntity() 的 Instant.parse 加安全处理
- [x] 5. WalletRepository 同步所有 division
- [x] 6. MarketRepository/IndustryRepository 不再静默吞异常

## MINOR
- [x] 7. 提取 formatIsk/formatNumber/formatDate/formatTimeAgo 到 FormatUtils.kt
- [x] 8. 魔法数字改用 Constants.ESI_PAGE_DELAY_MS
- [x] 9. HangarRepository division 回退加防御

## NIT
- [x] 10. IndustryRepository 未知 activity 加日志
