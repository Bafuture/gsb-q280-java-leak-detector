package com.example.gsb.leakdetector;

import java.util.Set;

/**
 * Location in user code where a resource was created. Captured at registration
 * time so a leaked resource can be traced back to its creation point.
 */
public record CallSite(String className, String methodName, String fileName, int lineNumber) {

    public static final CallSite UNKNOWN = new CallSite("<unknown>", "<unknown>", "<unknown>", -1);

    /** Framework classes that may sit between user code and the capture point. */
    private static final Set<String> FRAMEWORK_CLASSES = Set.of(
            CallSite.class.getName(),
            ResourceRegistry.class.getName(),
            ResourceLease.class.getName(),
            ResourceProvider.class.getName(),
            RegistryBackedProvider.class.getName(),
            LeakDetector.class.getName(),
            LeakDetection.class.getName(),
            LeakDetectionExtension.class.getName());

    /**
     * Captures the first stack frame outside of the leak-detector framework,
     * i.e. the user call site that triggered the registration.
     */
    public static CallSite capture() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames
                        .filter(frame -> !isFrameworkFrame(frame.getClassName()))
                        .findFirst()
                        .map(frame -> new CallSite(
                                frame.getClassName(),
                                frame.getMethodName(),
                                frame.getFileName(),
                                frame.getLineNumber()))
                        .orElse(UNKNOWN));
    }

    private static boolean isFrameworkFrame(String className) {
        int nested = className.indexOf('$');
        String topLevel = nested < 0 ? className : className.substring(0, nested);
        return FRAMEWORK_CLASSES.contains(topLevel);
    }

    @Override
    public String toString() {
        return className + "." + methodName + "(" + fileName + ":" + lineNumber + ")";
    }
}
