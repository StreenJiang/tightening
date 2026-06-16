package com.tightening.config;

import io.netty.channel.nio.NioEventLoopGroup;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NettyConfig {

    private NioEventLoopGroup sharedGroup;

    @Bean
    public NioEventLoopGroup nioEventLoopGroup() {
        sharedGroup = new NioEventLoopGroup(4);
        return sharedGroup;
    }

    @PreDestroy
    public void shutdown() {
        if (sharedGroup != null) {
            sharedGroup.shutdownGracefully();
        }
    }
}
