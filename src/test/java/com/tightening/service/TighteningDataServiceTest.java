package com.tightening.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.tightening.entity.TighteningData;
import com.tightening.mapper.TighteningDataMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TighteningDataServiceTest {

    @Mock
    private TighteningDataMapper tighteningDataMapper;

    private TighteningDataService tighteningDataService;

    @BeforeEach
    void setUp() {
        tighteningDataService = new TighteningDataService();
        ReflectionTestUtils.setField(tighteningDataService, "baseMapper", tighteningDataMapper);
    }

    @Test
    void save_shouldInsertEntity() {
        TighteningData data = new TighteningData();
        data.setTighteningId(100L);
        data.setTorque(45.5);
        data.setVin("VIN001");

        tighteningDataService.save(data);

        verify(tighteningDataMapper).insert(data);
    }

    @Test
    void getById_shouldReturnEntity() {
        TighteningData expected = new TighteningData();
        expected.setId(1L);
        expected.setTighteningId(100L);
        expected.setTorque(45.5);
        when(tighteningDataMapper.selectById(1L)).thenReturn(expected);

        TighteningData result = tighteningDataService.getById(1L);

        assertThat(result).isSameAs(expected);
        assertThat(result.getTorque()).isEqualTo(45.5);
        verify(tighteningDataMapper).selectById(1L);
    }

    @Test
    void list_shouldReturnAllEntities() {
        TighteningData d1 = new TighteningData();
        d1.setId(1L);
        d1.setVin("VIN001");
        TighteningData d2 = new TighteningData();
        d2.setId(2L);
        d2.setVin("VIN002");
        List<TighteningData> expected = List.of(d1, d2);
        when(tighteningDataMapper.selectList(any(Wrapper.class))).thenReturn(expected);

        List<TighteningData> result = tighteningDataService.list();

        assertThat(result).hasSize(2).containsExactly(d1, d2);
        verify(tighteningDataMapper).selectList(any(Wrapper.class));
    }

    @Test
    void updateById_shouldUpdateEntity() {
        TighteningData data = new TighteningData();
        data.setId(1L);
        data.setTorque(99.9);

        tighteningDataService.updateById(data);

        verify(tighteningDataMapper).updateById(data);
    }

    @Test
    void removeById_shouldDeleteEntity() {
        Long id = 5L;
        when(tighteningDataMapper.deleteById(id)).thenReturn(1);

        boolean result = tighteningDataService.removeById(id);

        assertThat(result).isTrue();
        verify(tighteningDataMapper).deleteById(id);
    }
}
