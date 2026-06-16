package com.tightening.netty.protocol.handler.atlas;

import com.tightening.constant.atlas.AtlasCommandType;
import com.tightening.constant.atlas.AtlasErrorCode;
import com.tightening.device.handler.ToolHandler;
import com.tightening.device.handler.impl.TCPDeviceHandler;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.netty.protocol.codec.atlas.AtlasFrame;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@ExtendWith(MockitoExtension.class)
@DisplayName("AtlasPFSeriesInBoundHandler processes AtlasFrame by MID")
class AtlasPFSeriesInBoundHandlerTest {

    private static final long TEST_DEVICE_ID = 100L;

    @Mock
    private ToolHandler deviceHandler;

    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        lenient().when(deviceHandler.generateKey(any(), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0) + ":" + invocation.getArgument(1));

        channel = new EmbeddedChannel(new AtlasPFSeriesInBoundHandler(deviceHandler));
        channel.attr(TCPDeviceHandler.DEVICE_ID).set(TEST_DEVICE_ID);
    }

    @AfterEach
    void cleanUp() {
        channel.finish();
    }

    @Test
    @DisplayName("HEARTBEAT frame: addResultFuture(true)")
    void testHeartbeat() {
        AtlasFrame frame = new AtlasFrame(AtlasCommandType.HEARTBEAT.getMid());
        channel.writeInbound(frame);

        verify(deviceHandler).addResultFuture(
                AtlasCommandType.HEARTBEAT.getMid() + ":" + TEST_DEVICE_ID, true);
    }

    @Test
    @DisplayName("CONNECT_ACK frame: addResultFuture(true)")
    void testConnectAck() {
        AtlasFrame frame = new AtlasFrame(AtlasCommandType.CONNECT_ACK.getMid());
        channel.writeInbound(frame);

        verify(deviceHandler).addResultFuture(
                AtlasCommandType.CONNECT_ACK.getMid() + ":" + TEST_DEVICE_ID, true);
    }

    @Test
    @DisplayName("NEGATIVE_ACK with valid error code: addErrorMsgFuture and handle result")
    void testNegativeAckWithErrorCode() {
        // Data: first 4 bytes = original command MID (SUBSCRIBE_DATA=60), next 2 bytes = error code (1=INVALID_DATA)
        byte[] data = "006001".getBytes(StandardCharsets.US_ASCII);
        AtlasFrame frame = new AtlasFrame(AtlasCommandType.NEGATIVE_ACK.getMid(), data);
        channel.writeInbound(frame);

        // Should add error message future with the error description
        verify(deviceHandler).addErrorMsgFuture(
                AtlasCommandType.NEGATIVE_ACK.getMid() + ":" + TEST_DEVICE_ID,
                AtlasErrorCode.INVALID_DATA.getDescription());

        // Should also handle the result (SUBSCRIBE_DATA case is a no-op, so no addResultFuture)
        verify(deviceHandler, never()).addResultFuture(anyString(), anyBoolean());
    }

    @Test
    @DisplayName("NEGATIVE_ACK without error code: handle result only")
    void testNegativeAckWithoutErrorCode() {
        // Data: first 4 bytes = original command MID (SUBSCRIBE_DATA=60), no error code
        byte[] data = "0060  ".getBytes(StandardCharsets.US_ASCII);
        AtlasFrame frame = new AtlasFrame(AtlasCommandType.NEGATIVE_ACK.getMid(), data);
        channel.writeInbound(frame);

        // No error code → addErrorMsgFuture should NOT be called
        verify(deviceHandler, never()).addErrorMsgFuture(anyString(), anyString());
    }

    @Test
    @DisplayName("POSITIVE_ACK for CONNECT: addResultFuture with CONNECT_ACK key")
    void testPositiveAckForConnect() {
        // Data: first 4 bytes = original command MID (CONNECT=1)
        // handlePositiveOrNegativeResult re-keys via generateKey(CONNECT_ACK enum, deviceId)
        // so the key uses the enum's toString() format "0002 (连接设备_成功反馈)"
        byte[] data = "0001".getBytes(StandardCharsets.US_ASCII);
        AtlasFrame frame = new AtlasFrame(AtlasCommandType.POSITIVE_ACK.getMid(), data);
        channel.writeInbound(frame);

        String expectedKey = AtlasCommandType.CONNECT_ACK.toString() + ":" + TEST_DEVICE_ID;
        verify(deviceHandler).addResultFuture(eq(expectedKey), eq(true));
    }

    @Test
    @DisplayName("POSITIVE_ACK for ENABLE: addResultFuture with ENABLE key")
    void testPositiveAckForEnable() {
        // Data: first 4 bytes = original command MID (ENABLE=43)
        byte[] data = "0043".getBytes(StandardCharsets.US_ASCII);
        AtlasFrame frame = new AtlasFrame(AtlasCommandType.POSITIVE_ACK.getMid(), data);
        channel.writeInbound(frame);

        verify(deviceHandler).addResultFuture(
                AtlasCommandType.ENABLE.getMid() + ":" + TEST_DEVICE_ID, true);
    }

    @Test
    @DisplayName("POSITIVE_ACK for DISABLE: addResultFuture with DISABLE key")
    void testPositiveAckForDisable() {
        byte[] data = "0042".getBytes(StandardCharsets.US_ASCII);
        AtlasFrame frame = new AtlasFrame(AtlasCommandType.POSITIVE_ACK.getMid(), data);
        channel.writeInbound(frame);

        verify(deviceHandler).addResultFuture(
                AtlasCommandType.DISABLE.getMid() + ":" + TEST_DEVICE_ID, true);
    }

    @Test
    @DisplayName("TIGHTEN_DATA frame: parse and delegate to handleTighteningData")
    void testTighteningData() {
        // Rev 1 tightening data (250 bytes, same layout as parser test)
        byte[] data = new byte[250];
        Arrays.fill(data, (byte) ' ');

        writeAt(data, 23, "1");
        writeAt(data, 29, "1");
        writeAt(data, 33, "CONTROLLER-01");
        writeAt(data, 60, "VIN1234567890");
        writeAt(data, 87, "1");
        writeAt(data, 91, "5");
        writeAt(data, 96, "10");
        writeAt(data, 102, "3");
        writeAt(data, 108, "1");
        writeAt(data, 111, "0");
        writeAt(data, 114, "1");
        writeAt(data, 117, "001000");
        writeAt(data, 125, "002000");
        writeAt(data, 133, "001500");
        writeAt(data, 141, "001234");
        writeAt(data, 149, "00010");
        writeAt(data, 156, "00100");
        writeAt(data, 163, "00050");
        writeAt(data, 170, "00045");
        writeAt(data, 177, "2024-01-15:10:30:00");
        writeAt(data, 219, "1");
        writeAt(data, 222, "1234567890");

        AtlasFrame frame = new AtlasFrame(AtlasCommandType.TIGHTEN_DATA.getMid(), 1, data);
        channel.writeInbound(frame);

        ArgumentCaptor<TighteningDataDTO> captor = ArgumentCaptor.forClass(TighteningDataDTO.class);
        verify(deviceHandler).handleTighteningData(captor.capture(), any());

        TighteningDataDTO dto = captor.getValue();
        assertThat(dto.getRevision()).isEqualTo(1);
        assertThat(dto.getTighteningId()).isEqualTo(1234567890L);
        assertThat(dto.getTorque()).isCloseTo(12.34, within(0.01));
        assertThat(dto.getAngle()).isCloseTo(45.0, within(0.01));
        assertThat(dto.getControllerName()).isEqualTo("CONTROLLER-01");
        assertThat(dto.getTimestamp()).isEqualTo("2024-01-15:10:30:00");
    }

    // ============== helper ==============

    private static void writeAt(byte[] data, int protocolByte, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, data, protocolByte - 21, bytes.length);
    }

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.api.Assertions.within(tolerance);
    }
}
