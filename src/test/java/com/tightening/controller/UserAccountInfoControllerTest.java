package com.tightening.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tightening.dto.UserAccountInfoDTO;
import com.tightening.service.UserAccountInfoService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class UserAccountInfoControllerTest {

    @Mock
    private UserAccountInfoService userAccountInfoService;

    @InjectMocks
    private UserAccountInfoController userAccountInfoController;

    @Test
    void shouldReturnUserList() {
        UserAccountInfoDTO user1 = new UserAccountInfoDTO().setStaffId("001").setName("Alice");
        UserAccountInfoDTO user2 = new UserAccountInfoDTO().setStaffId("002").setName("Bob");
        List<UserAccountInfoDTO> mockList = List.of(user1, user2);
        when(userAccountInfoService.getUserList()).thenReturn(mockList);

        ResponseEntity<List<UserAccountInfoDTO>> response = userAccountInfoController.getUsers();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(mockList).hasSize(2);
        verify(userAccountInfoService).getUserList();
    }

    @Test
    void shouldReturnUserById() {
        UserAccountInfoDTO mockUser = new UserAccountInfoDTO().setStaffId("001").setName("Alice");
        when(userAccountInfoService.getUserById(1L)).thenReturn(mockUser);

        ResponseEntity<UserAccountInfoDTO> response = userAccountInfoController.getUserById(1L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(mockUser);
        verify(userAccountInfoService).getUserById(1L);
    }

    @Test
    void shouldDeleteUserById() {
        when(userAccountInfoService.removeById(1L)).thenReturn(true);

        ResponseEntity<Boolean> response = userAccountInfoController.deleteUserById(1L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isTrue();
        verify(userAccountInfoService).removeById(1L);
    }
}
