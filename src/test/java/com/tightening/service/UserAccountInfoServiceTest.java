package com.tightening.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tightening.dto.UserAccountInfoDTO;
import com.tightening.entity.UserAccountInfo;
import com.tightening.mapper.UserAccountInfoMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserAccountInfoServiceTest {

    @Mock
    private UserAccountInfoMapper userAccountInfoMapper;

    private UserAccountInfoService userAccountInfoService;

    @BeforeEach
    void setUp() {
        userAccountInfoService = new UserAccountInfoService();
        ReflectionTestUtils.setField(userAccountInfoService, "baseMapper", userAccountInfoMapper);
    }

    @Test
    void getUserList_shouldReturnAllUsersAsDTOs() {
        UserAccountInfo e1 = new UserAccountInfo();
        e1.setId(1L);
        e1.setStaffId("001");
        e1.setName("Alice");
        e1.setAccount("alice@corp");
        UserAccountInfo e2 = new UserAccountInfo();
        e2.setId(2L);
        e2.setStaffId("002");
        e2.setName("Bob");
        e2.setAccount("bob@corp");
        when(userAccountInfoMapper.selectList(any())).thenReturn(List.of(e1, e2));

        List<UserAccountInfoDTO> result = userAccountInfoService.getUserList();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStaffId()).isEqualTo("001");
        assertThat(result.get(0).getName()).isEqualTo("Alice");
        assertThat(result.get(0).getAccount()).isEqualTo("alice@corp");
        assertThat(result.get(1).getStaffId()).isEqualTo("002");
        assertThat(result.get(1).getName()).isEqualTo("Bob");
        assertThat(result.get(1).getAccount()).isEqualTo("bob@corp");
        verify(userAccountInfoMapper).selectList(any());
    }

    @Test
    void getUserList_shouldReturnEmptyListWhenNoUsers() {
        when(userAccountInfoMapper.selectList(any())).thenReturn(List.of());

        List<UserAccountInfoDTO> result = userAccountInfoService.getUserList();

        assertThat(result).isEmpty();
        verify(userAccountInfoMapper).selectList(any());
    }

    @Test
    void getUserById_shouldReturnDTOWithCopiedProperties() {
        UserAccountInfo entity = new UserAccountInfo();
        entity.setId(1L);
        entity.setStaffId("001");
        entity.setName("Alice");
        entity.setAccount("alice@corp");
        entity.setPosition("Engineer");
        when(userAccountInfoMapper.selectById(1L)).thenReturn(entity);

        UserAccountInfoDTO result = userAccountInfoService.getUserById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getStaffId()).isEqualTo("001");
        assertThat(result.getName()).isEqualTo("Alice");
        assertThat(result.getAccount()).isEqualTo("alice@corp");
        assertThat(result.getPosition()).isEqualTo("Engineer");
        verify(userAccountInfoMapper).selectById(1L);
    }

    @Test
    void getUserById_whenNotFound_shouldThrowIllegalArgumentException() {
        when(userAccountInfoMapper.selectById(99L)).thenReturn(null);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> userAccountInfoService.getUserById(99L)
        );

        verify(userAccountInfoMapper).selectById(99L);
    }
}
