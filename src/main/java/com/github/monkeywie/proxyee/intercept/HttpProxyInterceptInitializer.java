package com.github.monkeywie.proxyee.intercept;

import lombok.extern.slf4j.Slf4j;

/**
 * 代理 拦截 初始化类
 */
@Slf4j
public class HttpProxyInterceptInitializer {

    public HttpProxyInterceptInitializer(){
        log.debug("HttpProxyInterceptInitializer [ 代理 拦截 初始化类 ] 被创建");
    }

    public void init(HttpProxyInterceptPipeline pipeline) {
    }
}
