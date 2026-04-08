package com.tightening.netty.protocol.codec.atlas;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;


@SpringBootTest
class AtlasFrameCodecTest {
    private AtlasFrameCodec codec;
    private EmbeddedChannel channel;
    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private ByteBufAllocator alloc;

    @BeforeEach
    void setup() {
        codec = new AtlasFrameCodec();
        channel = new EmbeddedChannel(new LoggingHandler(LogLevel.DEBUG), codec);

        when(ctx.alloc()).thenReturn(alloc);
    }

    @AfterEach
    void cleanUp() {
        channel.finish();
    }

    @Test
    void testEncode() {
        AtlasFrame atlasFrame = AtlasFrame.enableTool();

        // 写入到出站
        channel.writeOutbound(atlasFrame);

        // 读取并验证
        ByteBuf buf = channel.readOutbound();
        assertNotNull(buf);
        buf.release();
    }

    @Test
    void testDecode() throws Exception {
        // 写入到入站
        String asciiStr = "11420900   1    000000000004382016-02-23:06:27:350000101050001022140080300000000.0013420010000000509008032021.00000000510\0";
        ByteBuf buf = Unpooled.copiedBuffer(asciiStr, StandardCharsets.US_ASCII);

        String hexStr = "000001a0024602e9038a043304de057f062206c8076e081608be09630a090ab10b560c010ca80d420dd90e6e0f070fa1103c10d6116d120712a4134213e21482152815cc1668170917a6184218e0197c1a181ab41b4d1be51c821d261dc41e5a1ef31f89201b20b1214921de2270234923d82470250d259e263526cb276327f9288e292829b92a512ae12b732bff2c9b2d1d2d9c2e1a2e992f192f9a301e30a9312b31b0323932c3335333e134793501359f363036bb374837d5386338ee397439fc3a873b0e3b923c153c983d2e3dae3e333eb43f3a3fbe403f40c8414f41d8426042e7436b43ed4467450d4593461f46af473b47c4485048da496149e84a6b4aef4b734bf54c6f4cfb4d6f4de54e574ed44f514fcf505350d7515951d8525f52e6536e53fb5487551d55a7562f56b7573d57c8584a58cd594f59cf5a535ad55b555bd65c555ce55d675de95e675ee15f605fdd605d60e861ac627462f3638b6414648364a964cf64d764bd649c647b6439640163b86380634c631b62fb62ef62f362ff6311631e6327633063336336633a6343634d6359636963796388639a63aa63b963c263cd63d063ce63cd63c963c863c563c263c363c363c363c363c463c363c663c363c463c463c363c463c263c063c063c063bd63b963b363ac63a4639d6396638f63876382637c637563726371636f6370636e636a6364635e6354634a63406339632e63266319630d630162f462e962e162d762ce62c862c462bd62b762b062aa62a06299628f6281627462666259624c623f623162236217620d61ff61f561eb61e261d761ce61bf61b061a26193618661766169615d6150614261356129611b610d60fe60f060e160d060c460b760ad609e609360876079606b605d604e603f6031601c600c5ffd5ff15fe35fd85fce5fc45fba5fb25fa75f9e5f925f855f775f6b5f5e5f4e5f405f325f255f125f065ef95eec5edb5ecc5ebe5eb05ea25e935e845e745e655e565e485e3b5e2d5e205e135e065df85de65dda5dcc5dc05db45dab5d9f5d965d8a5d7e5d725d685d5c5d505d475d3a5d2e5d225d145d085cf75ced5ce05cd25cc55ca35c955c845c705c7c5c5d5c505c455c355c2d5c225c165c0d5bff5bf15be25bd15bc45bb95ba75b9b5b8d5b7d5b6e5b5f5b515b435b325b255b155b055af55ae45ad35ac15ab05a9f5a8a5a795a645a545a425a2d5a1c5a0959f559e359cf59bc59aa599959875975596359525941592f591d5911590058f058e358d058c458b158a25890587f58665856584558335820580e57fb57e757d557c357af579d578a577757655751573d572b571956ff56ed56e456dc56cb56ba56ab569c568b567a566a565a564b563d562d561e5610560255f255e255d555c855ba55ac559d558e558255765568555b554e5542";
        byte[] data = ByteBufUtil.decodeHexDump(hexStr);

        buf.writeBytes(data);
        when(alloc.buffer()).thenReturn(buf);

        channel.writeInbound(buf);

        AtlasFrame decodedFrame = channel.readInbound();
        assertNotNull(decodedFrame);
    }
}