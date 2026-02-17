package com.kp.nsbh.tools;

import reactor.core.publisher.Mono;

public interface Tool {
    Mono<String> execute(String inputJson);
}
