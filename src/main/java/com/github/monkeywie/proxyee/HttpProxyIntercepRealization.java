package com.github.monkeywie.proxyee;

import com.github.monkeywie.proxyee.intercept.HttpProxyIntercept;
import com.github.monkeywie.proxyee.intercept.HttpProxyInterceptPipeline;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.Charset;

/**
 * @author ad
 */
@Slf4j
public class HttpProxyIntercepRealization extends HttpProxyIntercept {

    /**
     * 拦截代理服务器到目标服务器的请求头
     */
    @Override
    public void beforeRequest(Channel clientChannel, HttpRequest httpRequest, HttpProxyInterceptPipeline pipeline) throws Exception {
        log.trace("请求头数据 : \n{}",httpRequest);
        super.beforeRequest(clientChannel, httpRequest, pipeline);
    }

    /**
     * 拦截代理服务器到目标服务器的请求体
     */
    @Override
    public void beforeRequest(Channel clientChannel, HttpContent httpContent, HttpProxyInterceptPipeline pipeline) throws Exception {
        log.trace("请求体数据 : \n{}",httpContent.content().toString(Charset.defaultCharset()));
        super.beforeRequest(clientChannel, httpContent, pipeline);
    }

    /**
     * 拦截代理服务器到客户端的响应头
     */
    @Override
    public void afterResponse(Channel clientChannel, Channel proxyChannel, HttpResponse httpResponse, HttpProxyInterceptPipeline pipeline) throws Exception {
        log.trace("响应头数据 : \n{}",httpResponse);
        super.afterResponse(clientChannel, proxyChannel, httpResponse, pipeline);
    }

    /**
     * 拦截代理服务器到客户端的响应体
     */
    @Override
    public void afterResponse(Channel clientChannel, Channel proxyChannel, HttpContent httpContent, HttpProxyInterceptPipeline pipeline) throws Exception {
        log.trace("响应体数据 : \n{}",httpContent.content().toString(Charset.forName("GBK")));
        super.afterResponse(clientChannel, proxyChannel, httpContent, pipeline);
    }
}
