package com.tightening.controller;

import com.tightening.config.ToolCommonConfig;
import com.tightening.device.DeviceManager;
import com.tightening.device.handler.DeviceHandler;
import com.tightening.device.handler.ToolHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceControllerTest {

    @Mock
    private DeviceManager deviceManager;

    @Mock
    private ToolHandler toolHandler;

    @Mock
    private DeviceHandler deviceHandler;

    @Mock
    private ToolCommonConfig toolCommonConfig;

    @InjectMocks
    private DeviceController controller;

    @BeforeEach
    void setUp() {
        lenient().when(toolCommonConfig.getCmdTimeoutMs()).thenReturn(5000L);
    }

    // ============ enableTool ============

    @Test
    void enableTool_withToolHandler_shouldCallEnableToolOpAndReturn200() {
        when(deviceManager.getHandler(1L)).thenReturn(toolHandler);
        when(toolHandler.unlock(1L)).thenReturn(CompletableFuture.completedFuture(true));

        DeferredResult<ResponseEntity<Boolean>> result = controller.enableTool(1L);

        assertThat(result.getResult()).isNotNull();
        ResponseEntity<Boolean> response = (ResponseEntity<Boolean>) result.getResult();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isTrue();
        verify(toolHandler).unlock(1L);
    }

    @Test
    void enableTool_withToolHandlerAndException_shouldReturn500() {
        when(deviceManager.getHandler(1L)).thenReturn(toolHandler);
        when(toolHandler.unlock(1L)).thenReturn(CompletableFuture.failedFuture(new RuntimeException("fail")));

        DeferredResult<ResponseEntity<Boolean>> result = controller.enableTool(1L);

        assertThat(result.getResult()).isNotNull();
        ResponseEntity<Boolean> response = (ResponseEntity<Boolean>) result.getResult();
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isFalse();
    }

    @Test
    void enableTool_withNonToolHandler_shouldReturnOkFalse() {
        when(deviceManager.getHandler(1L)).thenReturn(deviceHandler);

        DeferredResult<ResponseEntity<Boolean>> result = controller.enableTool(1L);

        assertThat(result.getResult()).isNotNull();
        ResponseEntity<Boolean> response = (ResponseEntity<Boolean>) result.getResult();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isFalse();
    }

    // ============ disableTool ============

    @Test
    void disableTool_withToolHandler_shouldCallDisableToolOpAndReturn200() {
        when(deviceManager.getHandler(1L)).thenReturn(toolHandler);
        when(toolHandler.lock(1L)).thenReturn(CompletableFuture.completedFuture(true));

        DeferredResult<ResponseEntity<Boolean>> result = controller.disableTool(1L);

        assertThat(result.getResult()).isNotNull();
        ResponseEntity<Boolean> response = (ResponseEntity<Boolean>) result.getResult();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isTrue();
        verify(toolHandler).lock(1L);
    }

    @Test
    void disableTool_withNonToolHandler_shouldReturnOkFalse() {
        when(deviceManager.getHandler(1L)).thenReturn(deviceHandler);

        DeferredResult<ResponseEntity<Boolean>> result = controller.disableTool(1L);

        assertThat(result.getResult()).isNotNull();
        ResponseEntity<Boolean> response = (ResponseEntity<Boolean>) result.getResult();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isFalse();
    }

    // ============ getEnabled ============

    @Test
    void getEnabled_withToolHandlerAndEnabled_shouldReturnTrue() {
        when(deviceManager.getHandler(1L)).thenReturn(toolHandler);
        when(toolHandler.isUnlocked(1L)).thenReturn(true);

        ResponseEntity<Boolean> response = controller.getEnabled(1L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isTrue();
    }

    @Test
    void getEnabled_withToolHandlerAndDisabled_shouldReturnFalse() {
        when(deviceManager.getHandler(1L)).thenReturn(toolHandler);
        when(toolHandler.isUnlocked(1L)).thenReturn(false);

        ResponseEntity<Boolean> response = controller.getEnabled(1L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isFalse();
    }

    @Test
    void getEnabled_withNonToolHandler_shouldReturnFalse() {
        when(deviceManager.getHandler(1L)).thenReturn(deviceHandler);

        ResponseEntity<Boolean> response = controller.getEnabled(1L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isFalse();
    }

    // ============ sendPSet ============

    @Test
    void sendPSet_withToolHandler_shouldCallSendPSetOpAndReturn200() {
        when(deviceManager.getHandler(1L)).thenReturn(toolHandler);
        when(toolHandler.sendPSetOp(1L, 5)).thenReturn(CompletableFuture.completedFuture(true));

        DeferredResult<ResponseEntity<Boolean>> result = controller.sendPSet(1L, 5);

        assertThat(result.getResult()).isNotNull();
        ResponseEntity<Boolean> response = (ResponseEntity<Boolean>) result.getResult();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isTrue();
        verify(toolHandler).sendPSetOp(1L, 5);
    }

    @Test
    void sendPSet_withToolHandlerAndException_shouldReturn500() {
        when(deviceManager.getHandler(1L)).thenReturn(toolHandler);
        when(toolHandler.sendPSetOp(1L, 5)).thenReturn(CompletableFuture.failedFuture(new RuntimeException("fail")));

        DeferredResult<ResponseEntity<Boolean>> result = controller.sendPSet(1L, 5);

        assertThat(result.getResult()).isNotNull();
        ResponseEntity<Boolean> response = (ResponseEntity<Boolean>) result.getResult();
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isFalse();
    }

    @Test
    void sendPSet_withNonToolHandler_shouldReturnOkFalse() {
        when(deviceManager.getHandler(1L)).thenReturn(deviceHandler);

        DeferredResult<ResponseEntity<Boolean>> result = controller.sendPSet(1L, 5);

        assertThat(result.getResult()).isNotNull();
        ResponseEntity<Boolean> response = (ResponseEntity<Boolean>) result.getResult();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isFalse();
    }
}
