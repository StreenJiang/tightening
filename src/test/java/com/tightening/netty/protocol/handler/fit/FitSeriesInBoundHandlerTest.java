package com.tightening.netty.protocol.handler.fit;

import com.tightening.constant.fit.FitCommandType;
import com.tightening.device.handler.ToolHandler;
import com.tightening.device.handler.impl.TCPDeviceHandler;
import com.tightening.entity.TighteningData;
import com.tightening.netty.protocol.codec.fit.FitFrame;
import com.tightening.service.TighteningDataService;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FitSeriesInBoundHandlerTest {

    @Mock
    private ToolHandler deviceHandler;

    @Mock
    private TighteningDataService tighteningDataService;

    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        lenient().when(deviceHandler.generateKey(any(), anyLong())).thenReturn("test:key");
        lenient().when(deviceHandler.getTighteningDataService()).thenReturn(tighteningDataService);

        channel = new EmbeddedChannel(new FitSeriesInBoundHandler(deviceHandler));
        channel.attr(TCPDeviceHandler.DEVICE_ID).set(100L);
    }

    @AfterEach
    void cleanUp() {
        channel.finish();
    }

    @Test
    void heartbeatAck_shouldCompleteFutureWithTrue() {
        byte[] data = {0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00};
        FitFrame frame = new FitFrame(FitCommandType.HEARTBEAT_ACK.getCode(), data);

        channel.writeInbound(frame);

        verify(deviceHandler).addResultFuture(anyString(), eq(true));
    }

    @Test
    void parameterSet_withCommadOk_shouldCompleteFutureWithTrue() {
        FitFrame frame = new FitFrame(FitCommandType.PARAMETER_SET.getCode(), new byte[]{0x00});

        channel.writeInbound(frame);

        verify(deviceHandler).addResultFuture(anyString(), eq(true));
    }

    @Test
    void enableDisable_withNonOkResponse_shouldCompleteFutureWithFalse() {
        FitFrame frame = new FitFrame(FitCommandType.ENABLE_DISABLE.getCode(), new byte[]{0x01});

        channel.writeInbound(frame);

        verify(deviceHandler).addResultFuture(anyString(), eq(false));
    }

    @Test
    void tightenFinal_shouldParseAndSaveData() {
        ByteBuffer buffer = ByteBuffer.allocate(23).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(12345);           // tighteningId
        buffer.put((byte) 1);           // status = OK
        buffer.put((byte) 1);           // programNumber
        buffer.put((byte) 0);           // barcodeLength = 0
        buffer.put((byte) 0);           // skip byte (barcodeLength == 0 path)
        buffer.putFloat(10.5f);         // torque
        buffer.putFloat(90.0f);         // angle
        // BCD timestamp: 2024-06-15 10:30:00
        buffer.put((byte) 0x20);
        buffer.put((byte) 0x24);
        buffer.put((byte) 0x06);
        buffer.put((byte) 0x15);
        buffer.put((byte) 0x10);
        buffer.put((byte) 0x30);
        buffer.put((byte) 0x00);

        FitFrame frame = new FitFrame(FitCommandType.TIGHTEN_FINAL.getCode(), buffer.array());

        channel.writeInbound(frame);

        verify(tighteningDataService).save(any(TighteningData.class));
    }

    @Test
    void curve_shouldNotThrow() {
        ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(12345);           // tighteningId
        buffer.putFloat(0.1f);          // time
        buffer.putFloat(5.0f);          // torque
        buffer.putFloat(45.0f);         // angle

        FitFrame frame = new FitFrame(FitCommandType.CURVE.getCode(), buffer.array());

        assertDoesNotThrow(() -> channel.writeInbound(frame));
    }

    @Test
    void alarm_shouldNotThrow() {
        byte[] data = new byte[11];
        data[0] = 0x00;
        data[1] = 0x01;   // alarmCode = 1
        data[2] = 0x01;   // level = Warning
        data[3] = 0x00;   // infoLength = 0
        // BCD timestamp: 2024-06-15 10:30:00
        data[4] = (byte) 0x20;
        data[5] = (byte) 0x24;
        data[6] = 0x06;
        data[7] = 0x15;
        data[8] = 0x10;
        data[9] = 0x30;
        data[10] = 0x00;

        FitFrame frame = new FitFrame(FitCommandType.ALARM.getCode(), data);

        assertDoesNotThrow(() -> channel.writeInbound(frame));
    }

    @Test
    void unknownCmdType_shouldNotInteractWithHandler() {
        FitFrame frame = new FitFrame((byte) 0xFF, new byte[]{0x00});

        channel.writeInbound(frame);

        verify(deviceHandler, never()).addResultFuture(anyString(), anyBoolean());
    }
}
