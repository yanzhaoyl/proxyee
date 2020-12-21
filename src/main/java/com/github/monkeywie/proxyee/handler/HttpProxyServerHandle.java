package com.github.monkeywie.proxyee.handler;

import com.github.monkeywie.proxyee.crt.CertPool;
import com.github.monkeywie.proxyee.exception.HttpProxyExceptionHandle;
import com.github.monkeywie.proxyee.intercept.HttpProxyIntercept;
import com.github.monkeywie.proxyee.intercept.HttpProxyInterceptInitializer;
import com.github.monkeywie.proxyee.intercept.HttpProxyInterceptPipeline;
import com.github.monkeywie.proxyee.intercept.HttpTunnelIntercept;
import com.github.monkeywie.proxyee.proxy.ProxyConfig;
import com.github.monkeywie.proxyee.proxy.ProxyHandleFactory;
import com.github.monkeywie.proxyee.server.HttpProxyServer;
import com.github.monkeywie.proxyee.server.HttpProxyServerConfig;
import com.github.monkeywie.proxyee.util.ProtoUtil;
import com.github.monkeywie.proxyee.util.ProtoUtil.RequestProto;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

/**
 * 代理服务处理器
 * [ 继承 ChannelInboundHandlerAdapter ](https://www.freesion.com/article/10331105014/)
 */
@Slf4j
public class HttpProxyServerHandle extends ChannelInboundHandlerAdapter {

    private ChannelFuture cf;
    private String host;
    private int port;
    private boolean isSsl = false;
    private int status = 0;
    private HttpProxyServerConfig serverConfig;
    private ProxyConfig proxyConfig;
    private HttpProxyInterceptInitializer interceptInitializer;
    private HttpProxyInterceptPipeline interceptPipeline;
    private HttpTunnelIntercept tunnelIntercept;
    private HttpProxyExceptionHandle exceptionHandle;
    private List requestList;
    private boolean isConnect;

    public HttpProxyServerConfig getServerConfig() {
        return serverConfig;
    }

    public HttpProxyInterceptPipeline getInterceptPipeline() {
        return interceptPipeline;
    }

    public HttpProxyExceptionHandle getExceptionHandle() {
        return exceptionHandle;
    }

    public HttpProxyServerHandle(HttpProxyServerConfig serverConfig, HttpProxyInterceptInitializer interceptInitializer, HttpTunnelIntercept tunnelIntercept, ProxyConfig proxyConfig, HttpProxyExceptionHandle exceptionHandle) {
        log.debug("HttpProxyServerHandle [ 代理 服务 处理类 ] 被创建");
        this.serverConfig = serverConfig;
        this.proxyConfig = proxyConfig;
        this.interceptInitializer = interceptInitializer;
        this.tunnelIntercept = tunnelIntercept;
        this.exceptionHandle = exceptionHandle;
    }

    /**
     * channel 读取数据
     */
    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            // 第一次建立连接取host和端口号和处理代理握手
            if (status == 0) {
                log.debug("第一次建立连接");
                RequestProto requestProto = ProtoUtil.getRequestProto(request);
                // bad request
                if (requestProto == null) {
                    log.debug("未获取到 IP 端口 ssl 信息 , 通道 关闭 , 流程结束");
                    ctx.channel().close();
                    return;
                }
                status = 1;
                this.host = requestProto.getHost();
                this.port = requestProto.getPort();
                // 建立代理握手
                if ("CONNECT".equalsIgnoreCase(request.method().name())) {
                    log.debug("CONNECT 请求 , 直接响应 ok , 以便 建立 代理 握手");
                    status = 2;
                    HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpProxyServer.SUCCESS);
                    ctx.writeAndFlush(response);
                    log.debug("从 管道中 删除 指定名称 [ httpCodec ] 的 通道处理程序 [ ChannelHandler ]");
                    ctx.channel().pipeline().remove("httpCodec");
                    // fix issue #42 [ 处理CONNECT连接时，需要释放bytebuf，否则会内存泄露。 ](https://github.com/monkeyWie/proxyee/issues/42)
                    ReferenceCountUtil.release(msg);
                    return;
                }
            }
            interceptPipeline = buildPipeline();
            interceptPipeline.setRequestProto(new RequestProto(host, port, isSsl));
            // fix issue #27 [ 访问特定网页发生302重定向的次数过多的问题 ](https://github.com/monkeyWie/proxyee/issues/27)
            // TODO 没看懂这个修复过程
            if (request.uri().indexOf("/") != 0) {
                URL url = new URL(request.uri());
                request.setUri(url.getFile());
            }
            interceptPipeline.beforeRequest(ctx.channel(), request);
        } else if (msg instanceof HttpContent) {
            if (status != 2) {
                interceptPipeline.beforeRequest(ctx.channel(), (HttpContent) msg);
            } else {
                ReferenceCountUtil.release(msg);
                status = 1;
            }
        } else { // ssl和websocket的握手处理
            if (serverConfig.isHandleSsl()) {
                ByteBuf byteBuf = (ByteBuf) msg;
                if (byteBuf.getByte(0) == 22) {// ssl握手
                    isSsl = true;
                    int port = ((InetSocketAddress) ctx.channel().localAddress()).getPort();
                    SslContext sslCtx = SslContextBuilder
                            .forServer(serverConfig.getServerPriKey(), CertPool.getCert(port, this.host, serverConfig)).build();
                    ctx.pipeline().addFirst("httpCodec", new HttpServerCodec());
                    ctx.pipeline().addFirst("sslHandle", sslCtx.newHandler(ctx.alloc()));
                    // 重新过一遍pipeline，拿到解密后的的http报文
                    ctx.pipeline().fireChannelRead(msg);
                    return;
                }
            }
            handleProxyData(ctx.channel(), msg, false);
        }
    }

    /**
     * channel 取消注册事件
     */
    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        if (cf != null) {
            cf.channel().close();
        }
        ctx.channel().close();
    }

    /**
     * channel 捕获到异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cf != null) {
            cf.channel().close();
        }
        ctx.channel().close();
        exceptionHandle.beforeCatch(ctx.channel(), cause);
    }

    /**
     * channel 读取完毕事件
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        super.channelReadComplete(ctx);
    }

    /**
     * 处理代理数据
     */
    private void handleProxyData(Channel channel, Object msg, boolean isHttp) throws Exception {
        log.debug("处理代理数据 开始");
        if (cf == null) {
            // connection异常 还有HttpContent进来，不转发
            if (isHttp && !(msg instanceof HttpRequest)) {
                return;
            }
            ProxyHandler proxyHandler = ProxyHandleFactory.build(proxyConfig);
            /*
             * 添加SSL client hello的Server Name Indication extension(SNI扩展) 有些服务器对于client
             * hello不带SNI扩展时会直接返回Received fatal alert: handshake_failure(握手错误)
             * 例如：https://cdn.mdn.mozilla.net/static/img/favicon32.7f3da72dcea1.png
             */
            RequestProto requestProto;
            if (!isHttp) {
                requestProto = new RequestProto(host, port, isSsl);
                if (this.tunnelIntercept != null) {
                    this.tunnelIntercept.handle(requestProto);
                }
            } else {
                requestProto = interceptPipeline.getRequestProto();
                HttpRequest httpRequest = (HttpRequest) msg;
                // 检查requestProto是否有修改
                RequestProto newRP = ProtoUtil.getRequestProto(httpRequest);
                if (!newRP.equals(requestProto)) {
                    // 更新Host请求头
                    if ((requestProto.getSsl() && requestProto.getPort() == 443)
                            || (!requestProto.getSsl() && requestProto.getPort() == 80)) {
                        httpRequest.headers().set(HttpHeaderNames.HOST, requestProto.getHost());
                    } else {
                        httpRequest.headers().set(HttpHeaderNames.HOST, requestProto.getHost() + ":" + requestProto.getPort());
                    }
                }
            }
            ChannelInitializer channelInitializer = isHttp ? new HttpProxyInitializer(channel, requestProto, proxyHandler)
                    : new TunnelProxyInitializer(channel, proxyHandler);
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(serverConfig.getProxyLoopGroup()) // 注册线程池
                    .channel(NioSocketChannel.class) // 使用NioSocketChannel来作为连接用的channel类
                    .handler(channelInitializer);
            if (proxyConfig != null) {
                // 代理服务器解析DNS和连接
                bootstrap.resolver(NoopAddressResolverGroup.INSTANCE);
            }
            requestList = new LinkedList();
            cf = bootstrap.connect(requestProto.getHost(), requestProto.getPort());
            cf.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    future.channel().writeAndFlush(msg);
                    synchronized (requestList) {
                        requestList.forEach(obj -> future.channel().writeAndFlush(obj));
                        requestList.clear();
                        isConnect = true;
                    }
                } else {
                    requestList.forEach(obj -> ReferenceCountUtil.release(obj));
                    requestList.clear();
                    getExceptionHandle().beforeCatch(channel, future.cause());
                    future.channel().close();
                    channel.close();
                }
            });
        } else {
            synchronized (requestList) {
                if (isConnect) {
                    cf.channel().writeAndFlush(msg);
                } else {
                    requestList.add(msg);
                }
            }
        }
        log.debug("处理代理数据 结束");
    }

    private HttpProxyInterceptPipeline buildPipeline() {
        log.debug("进入 管道 拦截 逻辑");

        HttpProxyInterceptPipeline interceptPipeline = new HttpProxyInterceptPipeline(new HttpProxyIntercept() {
            /**
             * 请求头
             */
            @Override
            public void beforeRequest(Channel clientChannel, HttpRequest httpRequest, HttpProxyInterceptPipeline pipeline)
                    throws Exception {
                handleProxyData(clientChannel, httpRequest, true);
            }

            /**
             * 请求体
             */
            @Override
            public void beforeRequest(Channel clientChannel, HttpContent httpContent, HttpProxyInterceptPipeline pipeline)
                    throws Exception {
                handleProxyData(clientChannel, httpContent, true);
            }

            /**
             * 响应头
             */
            @Override
            public void afterResponse(Channel clientChannel, Channel proxyChannel, HttpResponse httpResponse,
                                      HttpProxyInterceptPipeline pipeline) throws Exception {
                clientChannel.writeAndFlush(httpResponse);
                if (HttpHeaderValues.WEBSOCKET.toString().equals(httpResponse.headers().get(HttpHeaderNames.UPGRADE))) {
                    // websocket转发原始报文
                    proxyChannel.pipeline().remove("httpCodec");
                    clientChannel.pipeline().remove("httpCodec");
                }
            }

            /**
             * 响应体
             */
            @Override
            public void afterResponse(Channel clientChannel, Channel proxyChannel, HttpContent httpContent,
                                      HttpProxyInterceptPipeline pipeline) throws Exception {
                clientChannel.writeAndFlush(httpContent);
            }
        });
        interceptInitializer.init(interceptPipeline);
        return interceptPipeline;
    }
}
