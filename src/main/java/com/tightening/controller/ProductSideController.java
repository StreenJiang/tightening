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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("api/sides")
@RequiredArgsConstructor
public class ProductSideController {

    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_JPEG_VALUE, "image/gif", "image/webp");
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

    @PostMapping
    public ResponseEntity<ApiResponse<String>> create(@RequestBody ProductSideDTO dto) {
        ProductSide entity = Converter.dto2Entity(dto, ProductSide::new);
        sideService.saveOrUpdate(entity);
        return ResponseEntity.ok(ApiResponse.ok(String.valueOf(entity.getId())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> update(@PathVariable Long id, @RequestBody ProductSideDTO dto) {
        ProductSide entity = Converter.dto2Entity(dto, ProductSide::new);
        entity.setId(id);
        sideService.saveOrUpdate(entity);
        return ResponseEntity.ok(ApiResponse.ok(String.valueOf(entity.getId())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Long id) {
        sideService.cascadeDelete(id);
        return ResponseEntity.ok(ApiResponse.ok());
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

    @PutMapping("/{sideId}/image")
    public ResponseEntity<String> uploadImage(@PathVariable Long sideId,
                                              @RequestParam MultipartFile file) {
        return uploadImageData(sideId, file, ImageType.ORIGINAL);
    }

    @PutMapping("/{sideId}/image/rendered")
    public ResponseEntity<String> uploadRenderedImage(@PathVariable Long sideId,
                                                      @RequestParam MultipartFile file) {
        return uploadImageData(sideId, file, ImageType.RENDERED);
    }

    @PutMapping("/{sideId}/image/thumbnail")
    public ResponseEntity<String> uploadThumbnail(@PathVariable Long sideId,
                                                   @RequestParam MultipartFile file) {
        return uploadImageData(sideId, file, ImageType.THUMBNAIL);
    }

    private ResponseEntity<String> uploadImageData(Long sideId, MultipartFile file,
                                                   ImageType imageType) {
        try {
            if (file.getSize() > MAX_IMAGE_SIZE)
                return ResponseEntity.badRequest().body("图片大小超过 5MB 限制");
            if (file.getContentType() == null || !ALLOWED_IMAGE_TYPES.contains(file.getContentType()))
                return ResponseEntity.badRequest().body("不支持的文件类型");
            byte[] data = file.getBytes();
            boolean updated = switch (imageType) {
                case ORIGINAL -> sideService.updateImageData(sideId, data);
                case RENDERED -> sideService.updateRenderedImageData(sideId, data);
                case THUMBNAIL -> sideService.updateThumbnailData(sideId, data);
            };
            if (!updated) return ResponseEntity.notFound().build();
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.error("Image upload failed: sideId={}, type={}", sideId, imageType, e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}
