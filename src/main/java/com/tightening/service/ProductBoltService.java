package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.entity.BoltDeviceBinding;
import com.tightening.entity.BoltPartsBarcode;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductSide;
import com.tightening.i18n.BusinessException;
import com.tightening.mapper.ProductBoltMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class ProductBoltService extends ServiceImpl<ProductBoltMapper, ProductBolt> {
    private final BoltDeviceBindingService deviceBindingService;
    private final BoltPartsBarcodeService partsBarcodeService;
    private final ProductSideService sideService;

    public ProductBoltService(BoltDeviceBindingService deviceBindingService,
                              BoltPartsBarcodeService partsBarcodeService,
                              @Lazy ProductSideService sideService) {
        this.deviceBindingService = deviceBindingService;
        this.partsBarcodeService = partsBarcodeService;
        this.sideService = sideService;
    }

    public void saveBolt(ProductBolt entity) {
        validateSerialNumUnique(entity);
        saveOrUpdate(entity);
    }

    private void validateSerialNumUnique(ProductBolt entity) {
        Long sideId = entity.getProductSideId();
        if (sideId == null) return;
        long count = lambdaQuery()
                .eq(ProductBolt::getProductSideId, sideId)
                .eq(ProductBolt::getSerialNum, entity.getSerialNum())
                .eq(ProductBolt::getDeleted, 0)
                .ne(entity.getId() != null, ProductBolt::getId, entity.getId())
                .count();
        if (count > 0) throw BusinessException.of("bolt.serial.duplicate", entity.getSerialNum());
    }

    public List<ProductBolt> listBySideId(Long sideId) {
        return lambdaQuery()
                .eq(ProductBolt::getProductSideId, sideId)
                .eq(ProductBolt::getDeleted, 0)
                .orderByAsc(ProductBolt::getSerialNum)
                .list();
    }

    @Transactional
    public void deleteBoltsBySideIds(List<Long> sideIds) {
        if (sideIds.isEmpty()) return;
        List<Long> boltIds = lambdaQuery()
                .select(ProductBolt::getId)
                .in(ProductBolt::getProductSideId, sideIds)
                .list().stream().map(ProductBolt::getId).toList();
        if (!boltIds.isEmpty()) {
            deviceBindingService.lambdaUpdate().in(BoltDeviceBinding::getProductBoltId, boltIds).remove();
            partsBarcodeService.lambdaUpdate().in(BoltPartsBarcode::getProductBoltId, boltIds).remove();
            lambdaUpdate().in(ProductBolt::getId, boltIds).remove();
        }
    }

    @Transactional
    public void cascadeDelete(Long boltId) {
        deviceBindingService.lambdaUpdate()
                .eq(BoltDeviceBinding::getProductBoltId, boltId)
                .remove();
        partsBarcodeService.lambdaUpdate()
                .eq(BoltPartsBarcode::getProductBoltId, boltId)
                .remove();
        removeById(boltId);
    }

    public List<ProductBolt> listByTaskId(Long taskId) {
        Set<Long> sideIds = new java.util.HashSet<>(sideService.listSideIdsByTaskId(taskId));
        if (sideIds.isEmpty()) return List.of();
        return lambdaQuery()
                .in(ProductBolt::getProductSideId, sideIds)
                .orderByAsc(ProductBolt::getSerialNum)
                .list();
    }
}
