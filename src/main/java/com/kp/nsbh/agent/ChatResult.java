package com.kp.nsbh.agent;

import com.kp.nsbh.tools.ToolExecutionResult;
import java.util.List;

public record ChatResult(
        String assistantMessage,
        List<ToolExecutionResult> toolCalls
) {
}
