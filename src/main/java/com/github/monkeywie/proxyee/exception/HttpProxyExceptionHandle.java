package com.github.monkeywie.proxyee.exception;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

/**
 * 代理 异常 处理类
 * @author LiuJiawei
 */
@Slf4j
public class HttpProxyExceptionHandle {

    public  HttpProxyExceptionHandle(){
      log.debug("HttpProxyExceptionHandle [ 代理 异常 处理类 ] 被创建");
    }

    public void beforeCatch(Channel clientChannel, Throwable cause) throws Exception {
        throw new Exception(cause);
    }

    public void afterCatch(Channel clientChannel, Channel proxyChannel, Throwable cause)
            throws Exception {
        throw new Exception(cause);
    }
}
