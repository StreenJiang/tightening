package com.tightening.device.handler.impl.sudong;

import com.tightening.constant.sudongx7.SudongX7Constants;
import com.tightening.device.handler.ToolHandler;
import com.tightening.device.handler.impl.TCPDeviceHandler;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.netty.protocol.codec.sudongx7.SudongX7Frame;
import com.tightening.netty.protocol.handler.sudongx7.SudongX7InBoundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SudongX7InBoundHandler")
class SudongX7InBoundHandlerTest {

    @Mock
    private ToolHandler deviceHandler;

    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        lenient().when(deviceHandler.generateKey(any(), anyLong())).thenReturn("test:key");
        lenient().when(deviceHandler.generateKey(anyInt(), anyLong())).thenReturn("test:key");

        channel = new EmbeddedChannel(new SudongX7InBoundHandler(deviceHandler));
        channel.attr(TCPDeviceHandler.DEVICE_ID).set(100L);
    }

    @AfterEach
    void cleanUp() {
        channel.finish();
    }

    @Test
    @DisplayName("tool running frame should complete future with true")
    void toolRunning_shouldCompleteFuture() {
        SudongX7Frame frame = new SudongX7Frame(SudongX7Constants.CMD_TOOL_RUNNING, new byte[]{0x00});

        channel.writeInbound(frame);

        verify(deviceHandler).addResultFuture(anyString(), eq(true));
    }

    @Test
    @DisplayName("error frame should complete future with false")
    void error_shouldCompleteFuture() {
        SudongX7Frame frame = new SudongX7Frame(SudongX7Constants.CMD_ERROR, new byte[]{0x01});

        channel.writeInbound(frame);

        verify(deviceHandler).addResultFuture(anyString(), eq(false));
    }

    @Test
    @DisplayName("PSet response frame should complete future with true")
    void psetResponse_shouldCompleteFuture() {
        SudongX7Frame frame = new SudongX7Frame(SudongX7Constants.CMD_PSET_RESPONSE, new byte[]{0x00});

        channel.writeInbound(frame);

        verify(deviceHandler).addResultFuture(anyString(), eq(true));
    }

    @Test
    @DisplayName("tightening data frame should parse and delegate to handler")
    void tighteningData_shouldDelegateToHandler() {
        // Parser expects at least 35 bytes
        byte[] data = new byte[35];
        data[17] = 1; // status=OK
        SudongX7Frame frame = new SudongX7Frame(SudongX7Constants.CMD_TIGHTENING_DATA, data);

        channel.writeInbound(frame);

        verify(deviceHandler).handleTighteningData(any(TighteningDataDTO.class), any());
    }

    @Test
    @DisplayName("unknown cmd should not interact with handler")
    void unknownCmd_shouldNotInteract() {
        SudongX7Frame frame = new SudongX7Frame(0xFFFF, new byte[]{0x00});

        channel.writeInbound(frame);

        verify(deviceHandler, never()).addResultFuture(anyString(), anyBoolean());
        verify(deviceHandler, never()).handleTighteningData(any(), any());
    }
}
