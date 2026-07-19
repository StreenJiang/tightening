package com.tightening.controller;

import com.tightening.dto.ApiResponse;
import com.tightening.dto.ProductBoltDTO;
import com.tightening.entity.ProductBolt;
import com.tightening.service.ProductBoltService;
import com.tightening.util.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("api/bolts")
@RequiredArgsConstructor
public class ProductBoltController {

    private final ProductBoltService boltService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductBoltDTO>>> list(@RequestParam Long sideId) {
        List<ProductBolt> bolts = boltService.listBySideId(sideId);
        return ResponseEntity.ok(ApiResponse.ok(Converter.entity2Dto(bolts, ProductBoltDTO::new)));
    }
}
