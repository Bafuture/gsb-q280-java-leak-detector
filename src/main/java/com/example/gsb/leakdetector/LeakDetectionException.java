package com.example.gsb.leakdetector;

/** Thrown when a leak check fails. Extends AssertionError so it fails tests directly. */
public class LeakDetectionException extends AssertionError {

    private static final long serialVersionUID = 1L;

    public LeakDetectionException(String message) {
        super(message);
    }
}
