package com.example.gsb.leakdetector;

/** Kinds of resources the framework can watch. */
public enum ResourceType {
    THREAD,
    FILE_HANDLE,
    CONNECTION
}
