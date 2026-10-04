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

## 框架说明

实现位于 `com.example.gsb.leakdetector` 包，核心组件：

| 组件 | 职责 |
|------|------|
| `ResourceRegistry` / `ResourceLease` | 线程安全的资源登记处；创建即登记（自动捕获调用点），释放即注销 |
| `ResourceProvider` | 可替换的资源提供者（函数式接口），默认 `RegistryBackedProvider`，测试可用 lambda 模拟线程/句柄/连接数 |
| `ResourceSnapshot` | 某一时刻各类型资源数量与存活实例的不可变快照 |
| `LeakDetector` / `LeakPolicy` | 快照对比与泄漏判定；策略含允许增长类型与延迟回收容忍窗口 |
| `LeakReport` | 判定结果：泄漏明细（含创建调用点）、被忽略的允许增长、复检次数 |
| `SuiteLeakTracker` / `SuiteLeakReport` | 套件级检测，把泄漏归因到首次引入它的测试 |
| `LeakStatistics` | 各测试资源增减、泄漏次数、被忽略的允许增长数 |
| `@DetectLeaks` / `LeakDetectionExtension` | JUnit 5 集成，支持 `TEST` / `SUITE` / `TEST_AND_SUITE` 粒度 |

### 用法示例

```java
@DetectLeaks(allowedGrowth = ResourceType.THREAD,   // 线程池预热等允许增长
             toleranceMillis = 500,                 // 延迟回收容忍窗口
             granularity = Granularity.TEST_AND_SUITE)
class MyServiceTest {
    @Test
    void usesConnection() {
        try (ResourceLease lease = LeakDetection.registry()
                .lease(ResourceType.CONNECTION, "jdbc:mock://db")) {
            // 被测代码；try 结束自动释放，不释放则测试失败并打印创建调用点
        }
    }
}
```

泄漏失败信息形如：`CONNECTION: +1` 并附 `jdbc:mock://db created at com.example.MyServiceTest.usesConnection(MyServiceTest.java:18)`，可直接定位到引入泄漏的调用点；套件级报告会指出具体是哪个测试引入的泄漏。

## JVM 上的局限与降低误报的建议

这类"前后快照对比"的检测在 JVM 上存在固有局限：

- **线程池与线程复用**：线程池（如 `ForkJoinPool`、连接池内部线程）会按需创建并长期持有工作线程，首次使用线程池的测试会"制造"线程数增长，但这并非泄漏。
- **JVM 内部线程**：GC、JIT 编译器（C1/C2）、VM 周期性任务等线程由 JVM 自行管理，数量随运行时间、堆压力变化，与被测代码无关。
- **JIT 与类加载**：JIT 编译/去优化、类的延迟加载会改变内存与线程基线；同一代码冷运行与热运行的资源画像不同。
- **GC 与终结时机不确定**：对象何时被回收、`Cleaner`/finalizer 何时执行不可预测，测试刚结束时资源可能"正在回收"，立即判定会误报。
- **句柄观测滞后**：通过 `/proc/self/fd` 或 `FileDescriptor` 统计句柄时，操作系统与 JVM 的关闭存在异步窗口。
- **连接池**：池化连接是刻意持有的，数量增长往往是池预热而非泄漏。

降低误报的建议：

- 使用 `toleranceMillis` 给异步回收留出宽限，检测器会在窗口内多次复检（本框架默认 50ms 轮询一次）。
- 用 `allowedGrowth` 显式豁免可解释的增长（如线程池预热），豁免量会被统计为"被忽略的允许增长"而非静默吞掉。
- 先跑一段 warm-up（或在套件级基线前完成池/JIT 预热），让基线在稳态下采集。
- 用可替换的 `ResourceProvider` 只统计业务自己登记的资源，而不是 JVM 全局线程/句柄数，从源头避开 JVM 内部噪音。
- 避免并行跑测试时共享同一个全局登记处；必要时每个测试类使用独立 `ResourceRegistry`。
- 对偶发误报的用例，结合统计（`LeakStatistics`）观察趋势，而不是单次失败就定责。

## 任务提示词

以下为本题完整的 User Prompt 原文，两次执行必须使用完全相同的文本。

我们的服务跑久了会累积未释放的线程、连接与临时文件，希望测试阶段就能发现泄漏。请从零实现一个资源泄漏检测框架。仓库目前只有一个空的 Maven 工程（pom.xml 只声明 JUnit 5 与 AssertJ）。要求：1) 支持在测试前后采集资源快照：活跃线程数、打开的文件句柄数与注册的连接数（用可替换的资源提供者模拟）；2) 支持泄漏判定：测试结束后资源数量多于开始时即视为泄漏，需排除允许增长的资源类型；3) 支持定位：泄漏资源的创建位置要能通过登记信息追溯到调用点；4) 支持延迟回收容忍：给一定等待时间后再次采集，避免把正在回收的资源误判为泄漏；5) 支持单测级与套件级两种检测粒度，套件级可定位到引入泄漏的具体测试；6) 支持统计：各测试的资源增减、泄漏次数与被忽略的允许增长数；7) 说明这类检测在 JVM 上的局限（例如线程池与 JIT 影响），并给出降低误报的建议；8) 测试覆盖资源快照、泄漏判定、调用点定位、延迟容忍、粒度选择与统计；`mvn -q verify` 一条命令跑通。

## 提交要求

1. 在本仓库中完成提示词要求的全部内容。
2. `./mvnw -q verify` 必须通过。
3. 完成后在所属分支（A 或 B）上提交，产物快照的父提交必须是初始环境快照。
