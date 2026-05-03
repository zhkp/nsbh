package com.kp.nsbh.agent;

import com.kp.nsbh.memory.entity.MessageEntity;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * LLM客户端接口，定义与大语言模型交互的核心方法。
 *
 * <p>该接口提供了与LLM进行对话、工具调用和消息摘要的功能。
 * 所有方法均返回Mono，支持响应式编程模型。</p>
 *
 * @author NSBH Team
 * @since 1.0.0
 */
public interface LlmClient {

    /**
     * 获取LLM的首次回复，用于处理用户消息并可能触发工具调用。
     *
     * <p>此方法发送用户消息和对话历史给LLM，返回LLM的回复。
     * 回复可能包含工具调用请求，需要进一步处理。</p>
     *
     * @param userMessage 用户输入的消息内容
     * @param model 使用的LLM模型名称
     * @param memoryWindow 对话历史消息列表，作为上下文提供给LLM
     * @return 包含LLM回复内容的Mono，可能包含工具调用请求
     */
    Mono<LlmReply> firstReply(String userMessage, String model, List<MessageEntity> memoryWindow);

    /**
     * 获取LLM的最终回复，在工具执行完成后调用。
     *
     * <p>此方法在工具执行完成后，将工具执行结果发送给LLM，
     * 获取LLM基于工具结果的最终回复。</p>
     *
     * @param userMessage 用户输入的消息内容
     * @param model 使用的LLM模型名称
     * @param toolResult 工具执行的结果，以JSON字符串形式提供
     * @param memoryWindow 对话历史消息列表，作为上下文提供给LLM
     * @return 包含LLM最终回复内容的Mono
     */
    Mono<String> finalReply(String userMessage, String model,
                             String toolResult, List<MessageEntity> memoryWindow);

    /**
     * 以流式方式获取LLM的首次回复，用于最终轮次的文字输出。
     *
     * @param userMessage 用户输入的消息内容
     * @param model 使用的LLM模型名称
     * @param memoryWindow 对话历史消息列表，作为上下文提供给LLM
     * @return 文字增量（delta）的Flux
     */
    Flux<String> streamFirstReply(String userMessage, String model,
                                   List<MessageEntity> memoryWindow);

    /**
     * 对消息列表进行摘要总结。
     *
     * <p>此方法将一组消息发送给LLM，要求其生成一个简洁的摘要。
     * 通常用于长对话的压缩，以减少后续请求的token消耗。</p>
     *
     * @param messages 需要摘要的消息列表
     * @param model 使用的LLM模型名称
     * @return 包含摘要内容的Mono
     */
    Mono<String> summarize(List<MessageEntity> messages, String model);
}
