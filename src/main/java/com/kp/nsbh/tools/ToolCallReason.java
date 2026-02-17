package com.kp.nsbh.tools;

public enum ToolCallReason {
    NONE,
    NOT_ALLOWED,
    PERMISSION_MISSING,
    NOT_REGISTERED,
    INPUT_TOO_LARGE,
    OUTPUT_TOO_LARGE,
    TIMEOUT,
    EXECUTION_ERROR,
    INTERRUPTED
}
