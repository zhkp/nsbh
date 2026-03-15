package com.kp.nsbh.tools;

import reactor.core.publisher.Mono;

/**
 * 工具接口，定义NSBH系统中所有可执行工具的标准契约。
 *
 * <p>该接口是NSBH工具系统的核心抽象，所有具体工具（如HTTP请求工具、时间工具等）
 * 都必须实现此接口。工具通过JSON格式接收输入参数，并返回JSON格式的执行结果。</p>
 *
 * <p>工具的执行是异步的，返回Mono以支持响应式编程模型。</p>
 *
 * @author NSBH Team
 * @since 1.0.0
 * @see ToolRegistry
 * @see ToolService
 */
public interface Tool {

    /**
     * 执行工具的核心方法。
     *
     * <p>此方法接收JSON格式的输入参数，执行工具逻辑，并返回JSON格式的结果。
     * 具体工具实现类需要解析输入JSON，执行相应操作，并将结果序列化为JSON字符串返回。</p>
     *
     * <p>示例：
     * <pre>
     * // HTTP GET工具示例输入
     * {"url": "https://api.example.com/data"}
     *
     * // 示例输出
     * {"status": 200, "body": "...", "headers": {...}}
     * </pre></p>
     *
     * @param inputJson 工具输入参数，JSON格式字符串
     * @return 包含工具执行结果的Mono，结果为JSON格式字符串
     * @throws ToolExecutionException 当工具执行失败时抛出
     */
    Mono<String> execute(String inputJson);
}
