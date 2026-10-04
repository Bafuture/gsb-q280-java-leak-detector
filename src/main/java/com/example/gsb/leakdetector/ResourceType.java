package com.example.gsb.leakdetector;

/**
 * 受监控的资源类别。新增类别时只需扩展本枚举，框架的快照、判定与统计逻辑会自动覆盖。
 */
public enum ResourceType {
    THREAD,
    FILE_HANDLE,
    CONNECTION
}
