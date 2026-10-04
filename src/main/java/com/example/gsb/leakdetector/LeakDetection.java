package com.example.gsb.leakdetector;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * 在测试类上启用资源泄漏检测。
 *
 * <pre>{@code
 * @LeakDetection(granularity = Granularity.PER_SUITE,
 *                allowedGrowth = ResourceType.FILE_HANDLE,
 *                graceMillis = 200)
 * class MyServiceTest { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(LeakDetectionExtension.class)
public @interface LeakDetection {

    Granularity granularity() default Granularity.PER_TEST;

    /**
     * 允许增长的资源类型：这些类型的数量增长不判定为泄漏。
     */
    ResourceType[] allowedGrowth() default {};

    /**
     * 延迟回收宽限期（毫秒）：初次判定泄漏后等待该时长并轮询重采。
     */
    long graceMillis() default 0;

    /**
     * 宽限期内的重采间隔（毫秒）。
     */
    long pollMillis() default 20;
}
