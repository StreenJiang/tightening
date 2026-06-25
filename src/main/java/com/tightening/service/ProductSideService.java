package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.constant.ImageType;
import com.tightening.entity.ProductBolt;
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

    public List<Long> listSideIdsByMissionId(Long missionId) {
        return lambdaQuery()
                .select(ProductSide::getId)
                .eq(ProductSide::getProductMissionId, missionId)
                .eq(ProductSide::getDeleted, 0)
                .list().stream().map(ProductSide::getId).toList();
    }

    public ProductSide getByIdWithoutBlobs(Long id) {
        return lambdaQuery()
                .select(ProductSide.class, info ->
                    !info.getColumn().equals("image_data")
                        && !info.getColumn().equals("rendered_image_data")
                        && !info.getColumn().equals("thumbnail_data"))
                .eq(ProductSide::getId, id)
                .one();
    }

    public List<ProductSide> listByMissionId(Long missionId) {
        return lambdaQuery()
                .select(ProductSide.class, info ->
                        !info.getColumn().equals("image_data")
                                && !info.getColumn().equals("rendered_image_data")
                                && !info.getColumn().equals("thumbnail_data"))
                .eq(ProductSide::getProductMissionId, missionId)
                .eq(ProductSide::getDeleted, 0)
                .list();
    }

    @Transactional
    public void cascadeDelete(Long sideId) {
        boltService.deleteBoltsBySideIds(List.of(sideId));
        removeById(sideId);
    }

    public void deleteBoltsBySideIds(List<Long> sideIds) {
        boltService.deleteBoltsBySideIds(sideIds);
    }

    public byte[] getImageData(Long sideId, ImageType type) {
        ProductSide side = lambdaQuery()
                .select(ProductSide.class, info -> switch (type) {
                    case ORIGINAL -> info.getColumn().equals("image_data");
                    case RENDERED -> info.getColumn().equals("rendered_image_data");
                    case THUMBNAIL -> info.getColumn().equals("thumbnail_data");
                })
                .eq(ProductSide::getId, sideId)
                .one();
        if (side == null) return null;
        return switch (type) {
            case ORIGINAL -> side.getImageData();
            case THUMBNAIL -> side.getThumbnailData();
            case RENDERED -> side.getRenderedImageData();
        };
    }

    public boolean updateImageData(Long sideId, byte[] data) {
        return lambdaUpdate().eq(ProductSide::getId, sideId).set(ProductSide::getImageData, data).update();
    }

    public boolean updateRenderedImageData(Long sideId, byte[] data) {
        return lambdaUpdate().eq(ProductSide::getId, sideId).set(ProductSide::getRenderedImageData, data).update();
    }

    public boolean updateThumbnailData(Long sideId, byte[] data) {
        return lambdaUpdate().eq(ProductSide::getId, sideId).set(ProductSide::getThumbnailData, data).update();
    }
}
