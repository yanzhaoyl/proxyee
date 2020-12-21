package com.github.monkeywie.proxyee;


import com.github.monkeywie.proxyee.exception.HttpProxyExceptionHandle;
import com.github.monkeywie.proxyee.intercept.HttpProxyInterceptInitializer;
import com.github.monkeywie.proxyee.intercept.HttpProxyInterceptPipeline;
import com.github.monkeywie.proxyee.intercept.common.CertDownIntercept;
import com.github.monkeywie.proxyee.server.HttpProxyServer;
import com.github.monkeywie.proxyee.server.HttpProxyServerConfig;
import io.netty.channel.*;

class HttpProxyIntercepRealizationTest {

    public static void main(String[] args) throws Exception {

        // 提高客户端的TLS版本
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.1,SSLv3");
        // 也可以 设置 JVM参数 [ -Dhttps.protocols=TLSv1.2,TLSv1.1,TLSv1.0,SSLv3,SSLv2Hello ]


        HttpProxyServerConfig config = new HttpProxyServerConfig();
        config.setHandleSsl(true);
        new HttpProxyServer()
                .serverConfig(config)
                .proxyInterceptInitializer(new HttpProxyInterceptInitializer() {
                    @Override
                    public void init(HttpProxyInterceptPipeline pipeline) {
                        //处理证书下载
                        pipeline.addLast(new CertDownIntercept());
                        // 自定义拦截实现 [输出 请求 响应 信息]
                        pipeline.addLast(new HttpProxyIntercepRealization());
                    }
                })
                .httpProxyExceptionHandle(new HttpProxyExceptionHandle() {
                    @Override
                    public void beforeCatch(Channel clientChannel, Throwable cause) throws Exception {
                        cause.printStackTrace();
                    }

                    @Override
                    public void afterCatch(Channel clientChannel, Channel proxyChannel, Throwable cause)
                            throws Exception {
                        cause.printStackTrace();
                    }
                })
                .start(9999);
    }
}