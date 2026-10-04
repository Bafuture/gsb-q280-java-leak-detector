package com.example.gsb.leakdetector;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Enables resource-leak detection on a test class.
 *
 * <pre>{@code
 * @DetectLeaks(allowedGrowth = ResourceType.THREAD, toleranceMillis = 500)
 * class MyServiceTest { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(LeakDetectionExtension.class)
public @interface DetectLeaks {

    /** Resource types whose growth is tolerated (recorded as ignored, never fails). */
    ResourceType[] allowedGrowth() default {};

    /** Grace period in milliseconds to wait for delayed reclamation before reporting. */
    long toleranceMillis() default 0;

    /** Whether to check after each test, after the whole suite, or both. */
    Granularity granularity() default Granularity.TEST_AND_SUITE;
}
