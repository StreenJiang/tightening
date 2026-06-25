package com.tightening.controller;

import com.tightening.dto.ProductBoltDTO;
import com.tightening.entity.ProductBolt;
import com.tightening.service.ProductBoltService;
import com.tightening.util.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.util.List;

@Slf4j
@RestController
@RequestMapping("api/bolts")
@RequiredArgsConstructor
public class ProductBoltController {

    private final ProductBoltService boltService;

    @GetMapping
    public ResponseEntity<List<ProductBoltDTO>> list(@RequestParam Long sideId) {
        List<ProductBolt> bolts = boltService.listBySideId(sideId);
        return ResponseEntity.ok(Converter.entity2Dto(bolts, ProductBoltDTO::new));
    }

    @PostMapping
    public ResponseEntity<String> create(@RequestBody ProductBoltDTO dto,
                                          @RequestParam Long missionId) {
        ProductBolt entity = Converter.dto2Entity(dto, ProductBolt::new);
        boltService.saveBolt(entity, missionId);
        return ResponseEntity.ok(String.valueOf(entity.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> update(@PathVariable Long id, @RequestBody ProductBoltDTO dto,
                                          @RequestParam Long missionId) {
        ProductBolt entity = Converter.dto2Entity(dto, ProductBolt::new);
        entity.setId(id);
        boltService.saveBolt(entity, missionId);
        return ResponseEntity.ok(String.valueOf(entity.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        boltService.cascadeDelete(id);
        return ResponseEntity.ok("ok");
    }
}
