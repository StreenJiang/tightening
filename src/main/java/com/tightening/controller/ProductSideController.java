package com.tightening.controller;

import com.tightening.constant.ImageType;
import com.tightening.dto.ApiResponse;
import com.tightening.dto.ProductSideDTO;
import com.tightening.entity.ProductSide;
import com.tightening.service.ProductSideService;
import com.tightening.util.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("api/sides")
@RequiredArgsConstructor
public class ProductSideController {

    private final ProductSideService sideService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductSideDTO>>> list(@RequestParam Long missionId) {
        List<ProductSide> sides = sideService.listByMissionId(missionId);
        return ResponseEntity.ok(ApiResponse.ok(Converter.entity2Dto(sides, ProductSideDTO::new)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductSideDTO>> get(@PathVariable Long id) {
        ProductSide side = sideService.getByIdWithoutBlobs(id);
        if (side == null) return ResponseEntity.ok(ApiResponse.fail("not found"));
        return ResponseEntity.ok(ApiResponse.ok(Converter.entity2Dto(side, ProductSideDTO::new)));
    }

    @GetMapping("/{sideId}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable Long sideId,
                                           @RequestParam(defaultValue = "rendered") String type) {
        ImageType imageType = ImageType.fromValue(type).orElse(ImageType.RENDERED);
        byte[] data = sideService.getImageData(sideId, imageType);
        if (data == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .body(data);
    }
}
