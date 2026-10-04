# 资源泄漏检测框架

Pair-wise GSB 标注任务仓库（第 17 批 / 280）。

| 项目 | 内容 |
|------|------|
| 任务类型 | Feature 迭代 |
| 任务难度 | 困难 |
| 语言/框架 | Java, Maven, JUnit 5 |
| 环境可复现等级 | 无外部依赖 |
| 构建方式 | Maven（含 mvnw wrapper，无需本机安装 Maven） |

> 本仓库是**初始环境快照**：只有工程骨架，不含任何实现代码。
> 分支说明：`main` 为初始环境；`A`、`B` 为两次独立执行各自的工作分支，均从 `main` 的同一个提交拉出。

## 运行方式

```bash
./mvnw -q verify
```

## 任务提示词

以下为本题完整的 User Prompt 原文，两次执行必须使用完全相同的文本。

我们的服务跑久了会累积未释放的线程、连接与临时文件，希望测试阶段就能发现泄漏。请从零实现一个资源泄漏检测框架。仓库目前只有一个空的 Maven 工程（pom.xml 只声明 JUnit 5 与 AssertJ）。要求：1) 支持在测试前后采集资源快照：活跃线程数、打开的文件句柄数与注册的连接数（用可替换的资源提供者模拟）；2) 支持泄漏判定：测试结束后资源数量多于开始时即视为泄漏，需排除允许增长的资源类型；3) 支持定位：泄漏资源的创建位置要能通过登记信息追溯到调用点；4) 支持延迟回收容忍：给一定等待时间后再次采集，避免把正在回收的资源误判为泄漏；5) 支持单测级与套件级两种检测粒度，套件级可定位到引入泄漏的具体测试；6) 支持统计：各测试的资源增减、泄漏次数与被忽略的允许增长数；7) 说明这类检测在 JVM 上的局限（例如线程池与 JIT 影响），并给出降低误报的建议；8) 测试覆盖资源快照、泄漏判定、调用点定位、延迟容忍、粒度选择与统计；`mvn -q verify` 一条命令跑通。

## 提交要求

1. 在本仓库中完成提示词要求的全部内容。
2. `./mvnw -q verify` 必须通过。
3. 完成后在所属分支（A 或 B）上提交，产物快照的父提交必须是初始环境快照。

---

## 实现说明

框架代码位于 `com.example.gsb.leakdetector`（`src/main/java`）。

| 组件 | 职责 |
|------|------|
| `ResourceType` | 受监控资源：`THREAD` / `FILE_HANDLE` / `CONNECTION` |
| `ResourceProvider` / `ResourceProviders` | 可替换的资源数据提供者，返回 `ResourceSnapshot`；测试用模拟实现，生产可接 JVM 真实指标 |
| `ResourceSnapshot` | 某时刻各资源数量的不可变快照，支持相对基线的有符号增减计算 |
| `ResourceRegistry` / `TrackedResource` | 资源登记处；创建资源时登记并自动捕获调用点（类、方法、行号），释放时注销 |
| `LeakDetector` | 判定器：结束数 > 开始数即泄漏；支持允许增长白名单与延迟回收宽限期（轮询重采） |
| `LeakReport` | 判定结果：增减量、泄漏、被忽略的增长、泄漏资源及其创建调用点 |
| `LeakStatistics` | 各测试资源增减、泄漏次数、被忽略的允许增长数聚合 |
| `Granularity` / `LeakDetectionSession` | 单测级（每方法前后对比）与套件级（类级基线，逐测试归因） |
| `LeakDetectionExtension` / `@LeakDetection` | JUnit 5 扩展，自动驱动会话 |
| `JvmResourceProviders` / `LeakDetectionEnvironment` | 默认 JVM 提供者（线程数 / `/proc/self/fd` / 登记连接数）与全局接线点 |

### 用法

在测试类上加注解即可；业务代码创建连接时通过登记处登记，泄漏时可定位到调用点：

```java
@LeakDetection(
        granularity = Granularity.PER_SUITE, // 或 PER_TEST
        allowedGrowth = ResourceType.FILE_HANDLE,
        graceMillis = 200)
class OrderServiceTest {
    @Test
    void closesConnection() {
        try (TrackedResource ignored =
                     LeakDetectionEnvironment.registry()
                             .register(ResourceType.CONNECTION, "jdbc:orders")) {
            service.query(); // 若忘记关闭，报告含 create 调用点
        }
    }
}
```

不依赖 JUnit 时也可直接使用：

```java
LeakDetector detector = LeakDetector.builder(myProvider)
        .allowedGrowth(Set.of(ResourceType.FILE_HANDLE))
        .gracePeriod(Duration.ofMillis(200))
        .registry(registry)
        .build();
ResourceSnapshot before = detector.snapshot();
// ... 执行被测逻辑 ...
LeakReport report = detector.check("myTest", before);
if (report.hasLeaks()) {
    throw new LeakDetectedException(report);
}
LeakStatistics stats = detector.statistics();
```

测试中通过 `LeakDetectionEnvironment.useProvider(...)` 注入 `SimulatedResources`
    （见 `src/test`），使判定逻辑完全确定、无环境噪声。

## 这类检测在 JVM 上的局限

- **线程池与常驻线程**：线程池按核心线程数、空闲超时等策略保留线程，任务结束后线程不会立刻销毁，极易被误判为线程泄漏；第三方库（JDBC 驱动、HTTP 客户端、日志框架）的内置线程同样如此。
- **JIT / GC 与清理时序**：JIT 编译线程、GC 线程、类加载（触发 Jar/zip 句柄打开与缓存）会让线程数和文件句柄数随时机变化；`finalize`/Cleaner/虚引用队列驱动的回收是异步的，测试结束瞬间资源可能尚未释放。
- **文件句柄计数的平台差异**：`/proc/self/fd` 仅在 Linux 可用，且包含管道、事件 fd、jar 内存映射等非业务句柄；macOS/Windows 需另接 lsof 或 OS API。
- **计数法只能证明“净增长”**：一次测试内泄漏一个又新建一个（数量不变）无法被发现；其他测试/后台任务并发增删也会污染计数。
- **`Thread.activeCount` 不可靠**：只统计调用线程所在线程组中的活动线程，默认提供者改用 `ThreadMXBean.getThreadCount()`，但它仍受平台线程与虚拟线程（Java 21+ 不计入平台线程计数）影响。
- **调用点依赖显式登记**：只有通过 `ResourceRegistry` 登记的资源才能追溯创建位置；未插桩的原生句柄只能报数量变化。

### 降低误报的建议

- **设置延迟回收宽限期**：`graceMillis` 给异步回收留出时间，框架会轮询重采而非只看一次（推荐 100–500ms，按 CI 机器速度调）。
- **测试前预热**：先跑若干空轮次触发 JIT 编译、类加载与连接池初始化，再进入正式检测。
- **使用允许增长白名单**：对线程池预热、JIT 类加载等已知只增不减的类型用 `allowedGrowth` 排除，并通过统计关注其增长幅度。
- **隔离执行环境**：Surefire fork 独立 JVM、关闭并行测试（`parallelExecution=false`），避免后台任务交叉污染；同一套件内使用固定大小的线程池并在 `@AfterAll` 显式 shutdown。
- **优先对可登记资源做强断言**：连接、临时文件通过 `ResourceRegistry`/try-with-resources 显式登记，数量 + 调用点双重证据；对原生线程/句柄指标只做趋势性检查。
- **重复执行取稳定结果**：偶发性增长可能是 GC/JIT 噪声，泄漏通常可稳定复现；可在 CI 上多次运行再判定。
