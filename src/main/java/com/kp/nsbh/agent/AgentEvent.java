package com.kp.nsbh.agent;

import com.kp.nsbh.tools.ToolCallReason;
import com.kp.nsbh.tools.ToolCallStatus;
import com.kp.nsbh.tools.ToolExecutionResult;
import java.util.List;

public sealed interface AgentEvent permits
        AgentEvent.TextDelta,
        AgentEvent.ToolStart,
        AgentEvent.ToolEnd,
        AgentEvent.Done {

    record TextDelta(String text) implements AgentEvent {}

    record ToolStart(String toolName, String toolCallId) implements AgentEvent {}

    record ToolEnd(String toolName, String toolCallId,
                   ToolCallStatus status, ToolCallReason reason, String result) implements AgentEvent {}

    record Done(String fullText, List<ToolExecutionResult> toolResults) implements AgentEvent {}
}
