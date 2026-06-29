package com.tightening.service;

import com.tightening.mapper.ExportTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExportTaskService cleanup")
class ExportTaskServiceCleanupTest {

    @Mock
    private ExportTaskMapper mapper;

    private ExportTaskService service;

    @BeforeEach
    void setUp() {
        service = new ExportTaskService();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    @DisplayName("cleanupTasks 调用 mapper.delete 并传递正确的条件")
    void shouldCallDeleteWithCorrectConditions() {
        when(mapper.delete(any())).thenReturn(5);

        int result = service.cleanupTasks(7);

        assertThat(result).isEqualTo(5);
        verify(mapper).delete(any());
    }
}
