package com.tightening.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.tightening.constant.DeviceType;
import com.tightening.device.DeviceManager;
import com.tightening.entity.Device;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class LoginControllerTest {

    @Mock
    private DeviceManager deviceManager;

    @InjectMocks
    private LoginController loginController;

    @Captor
    private ArgumentCaptor<List<Device>> deviceListCaptor;

    @Test
    void shouldReturnOkAndCallUserLoggedIn() {
        ResponseEntity<Void> response = loginController.userLogin();

        verify(deviceManager).userLoggedIn(deviceListCaptor.capture());
        List<Device> devices = deviceListCaptor.getValue();

        assertThat(devices).hasSize(1);
        Device device = devices.getFirst();
        assertThat(device.getId()).isEqualTo(1L);
        assertThat(device.getType()).isEqualTo(DeviceType.FIT_FTC6.getId());
        assertThat(device.getDetail()).contains("\"ip\"", "\"port\"");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNull();
    }
}
