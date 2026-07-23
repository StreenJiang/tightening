package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.entity.ProductSide;
import com.tightening.mapper.ProductSideMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSideService extends ServiceImpl<ProductSideMapper, ProductSide> {
    private final ProductBoltService boltService;

    public List<Long> listSideIdsByTaskId(Long taskId) {
        return lambdaQuery()
                .select(ProductSide::getId)
                .eq(ProductSide::getProductTaskId, taskId)
                .eq(ProductSide::getDeleted, 0)
                .list().stream().map(ProductSide::getId).toList();
    }

    @Transactional
    public void cascadeDelete(Long sideId) {
        boltService.deleteBoltsBySideIds(List.of(sideId));
        removeById(sideId);
    }

    public void deleteBoltsBySideIds(List<Long> sideIds) {
        boltService.deleteBoltsBySideIds(sideIds);
    }
}
