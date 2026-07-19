# Side 图片 Base64 改造 + 接口精简 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 side 图片从 multipart 上传改为 Base64 嵌入 JSON，删除 `/api/sides` 相关端点，精简任务接口。

**Architecture:** ProductMissionSaveDTO 重命名为 ProductMissionDetailDTO 同时承担保存入参和详情出参，ProductMissionDTO 保持列表专用。diffSides 直接从 DTO 字段取 Base64 解码，不再依赖 imageMap。

**Tech Stack:** Java 21, Spring Boot 3.5.10, MyBatis-Plus 3.5.9, Lombok

## Global Constraints

- 严禁全限定类名，所有类型通过 import 导入
- 使用 Lombok @Getter/@Setter/@Accessors(chain=true)
- 中文日志
- `java.util.Base64` 编解码

---

### Task 1: DTO 变更

**Files:**
- Modify: `src/main/java/com/tightening/dto/ProductSideSaveItem.java`
- Rename: `src/main/java/com/tightening/dto/ProductMissionSaveDTO.java` → `ProductMissionDetailDTO.java`

**Interfaces:**
- Produces: `ProductSideSaveItem.getImage()/getRenderedImage()/getThumbnail()` → String (Base64, null=保持, ""=删除)
- Produces: `ProductMissionDetailDTO` (same structure as old ProductMissionSaveDTO, new name)

- [ ] **Step 1: 给 ProductSideSaveItem 添加三个图片字段**

在 `src/main/java/com/tightening/dto/ProductSideSaveItem.java` 的 `bolts` 字段后添加：

```java
    private String image;
    private String renderedImage;
    private String thumbnail;
```

完整文件变为：

```java
package com.tightening.dto;

import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Setter
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class ProductSideSaveItem extends BaseDTO {
    private String name;
    private List<ProductBoltSaveItem> bolts;
    private String image;
    private String renderedImage;
    private String thumbnail;
}
```

- [ ] **Step 2: 重命名 ProductMissionSaveDTO → ProductMissionDetailDTO**

```bash
cd /Users/streen/IdeaProjects/tightening
mv src/main/java/com/tightening/dto/ProductMissionSaveDTO.java \
   src/main/java/com/tightening/dto/ProductMissionDetailDTO.java
```

- [ ] **Step 3: 更新类名和构造函数引用**

编辑 `src/main/java/com/tightening/dto/ProductMissionDetailDTO.java`：

```java
package com.tightening.dto;

import com.tightening.constant.InspectionScope;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Setter
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class ProductMissionDetailDTO extends BaseDTO {
    private String name;
    private Integer maxNgCount;
    private Integer passwordRequiredNgCount;
    private Integer enabled;
    private Integer multiDeviceIndependent;
    private Integer skipScrew;
    private Integer isInspection;
    private InspectionScope inspectionScope;

    private List<Long> inspectionBoundMissionIds;
    private List<PrerequisiteSaveItem> prerequisites;
    private List<BarCodeRuleSaveItem> barcodeRules;
    private List<ProductSideSaveItem> sides;
}
```

- [ ] **Step 4: 更新所有引用 ProductMissionSaveDTO 的文件**

在 `src/main/java/com/tightening/service/ProductMissionService.java:13`：
```java
// 改前
import com.tightening.dto.ProductMissionSaveDTO;
// 改后
import com.tightening.dto.ProductMissionDetailDTO;
```

在 `src/main/java/com/tightening/service/ProductMissionService.java:86`：
```java
// 改前
public ProductMissionSaveDTO saveMission(ProductMissionSaveDTO dto, Map<String, byte[]> imageMap) {
// 改后
public ProductMissionDetailDTO saveMission(ProductMissionDetailDTO dto) {
```

在 `src/main/java/com/tightening/controller/ProductMissionController.java:9`：
```java
// 改前
import com.tightening.dto.ProductMissionSaveDTO;
// 改后
import com.tightening.dto.ProductMissionDetailDTO;
```

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/tightening/dto/
git rm src/main/java/com/tightening/dto/ProductMissionSaveDTO.java
git add src/main/java/com/tightening/service/ProductMissionService.java
git add src/main/java/com/tightening/controller/ProductMissionController.java
git commit -m "refactor: add Base64 image fields to ProductSideSaveItem, rename ProductMissionSaveDTO to ProductMissionDetailDTO"
```

---

### Task 2: Service 层 — saveMission 和 diffSides 改造

**Files:**
- Modify: `src/main/java/com/tightening/service/ProductMissionService.java`

**Interfaces:**
- Consumes: `ProductMissionDetailDTO`, `ProductSideSaveItem.getImage()/getRenderedImage()/getThumbnail()`
- Produces: `saveMission(ProductMissionDetailDTO dto)` (去掉 imageMap 参数)

- [ ] **Step 1: 修改 saveMission 签名和实现**

`src/main/java/com/tightening/service/ProductMissionService.java:85-106`：

```java
    @Transactional
    public ProductMissionDetailDTO saveMission(ProductMissionDetailDTO dto) {
        ProductMission mission = Converter.dto2Entity(dto, ProductMission::new);
        if (mission.getInspectionScope() == null) mission.setInspectionScope(InspectionScope.NONE);
        saveOrUpdate(mission);
        Long missionId = mission.getId();
        dto.setId(missionId);

        validator.validateInspectionScope(mission, dto.getInspectionBoundMissionIds());

        syncInspectionBindings(missionId, dto.getInspectionBoundMissionIds());

        BarcodeDiffResult barcodeResult = diffBarcodeRules(missionId, dto.getBarcodeRules());
        validator.validateBarcodeRules(barcodeResult.rules());

        diffPrerequisites(missionId, dto.getPrerequisites(), barcodeResult);
        validator.validateInspectionChainSelfInspection(mission, dto.getPrerequisites());

        diffSides(missionId, dto.getSides(), barcodeResult);

        return dto;
    }
```

- [ ] **Step 2: 修改 diffSides 方法签名和图片处理逻辑**

`src/main/java/com/tightening/service/ProductMissionService.java:241-280`：

```java
    private void diffSides(Long missionId, List<ProductSideSaveItem> dtoSides,
                            BarcodeDiffResult barcodeResult) {
        List<ProductSide> existingSides = sideService.lambdaQuery()
                .eq(ProductSide::getProductMissionId, missionId)
                .eq(ProductSide::getDeleted, 0)
                .list();

        Set<Long> dtoSideIds = new HashSet<>();

        if (dtoSides != null) {
            for (int i = 0; i < dtoSides.size(); i++) {
                ProductSideSaveItem sideItem = dtoSides.get(i);
                ProductSide sideEntity = Converter.dto2Entity(sideItem, ProductSide::new);
                sideEntity.setProductMissionId(missionId);

                if (sideItem.getId() != null) {
                    // 更新：null 图片字段需保留 DB 原值
                    ProductSide existing = sideService.getById(sideItem.getId());
                    if (sideItem.getImage() == null && existing != null) {
                        sideEntity.setImageData(existing.getImageData());
                    }
                    if (sideItem.getRenderedImage() == null && existing != null) {
                        sideEntity.setRenderedImageData(existing.getRenderedImageData());
                    }
                    if (sideItem.getThumbnail() == null && existing != null) {
                        sideEntity.setThumbnailData(existing.getThumbnailData());
                    }
                    dtoSideIds.add(sideItem.getId());
                    applyImageFromBase64(sideItem, sideEntity);
                    sideService.updateById(sideEntity);
                } else {
                    applyImageFromBase64(sideItem, sideEntity);
                    sideService.save(sideEntity);
                    sideItem.setId(sideEntity.getId());
                }

                diffBolts(sideEntity.getId(), missionId, sideItem.getBolts(), barcodeResult);
            }
        }

        for (ProductSide ex : existingSides) {
            if (!dtoSideIds.contains(ex.getId())) {
                sideService.cascadeDelete(ex.getId());
            }
        }
    }

    private void applyImageFromBase64(ProductSideSaveItem item, ProductSide entity) {
        if (item.getImage() != null) {
            entity.setImageData(item.getImage().isEmpty() ? null :
                    java.util.Base64.getDecoder().decode(item.getImage()));
        }
        if (item.getRenderedImage() != null) {
            entity.setRenderedImageData(item.getRenderedImage().isEmpty() ? null :
                    java.util.Base64.getDecoder().decode(item.getRenderedImage()));
        }
        if (item.getThumbnail() != null) {
            entity.setThumbnailData(item.getThumbnail().isEmpty() ? null :
                    java.util.Base64.getDecoder().decode(item.getThumbnail()));
        }
    }
```

- [ ] **Step 3: 移除 Map import（如果不再需要）**

检查 `saveMission` 方法体中是否还有对 `Map` 的引用：`import java.util.Map;` — 如果 diffSides 签名变了之后不再需要 Map，则移除。但 `BarcodeDiffResult` 内部使用了 Map，所以保留。

- [ ] **Step 4: 运行编译检查**

```bash
cd /Users/streen/IdeaProjects/tightening && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/tightening/service/ProductMissionService.java
git commit -m "refactor: replace imageMap with Base64 decoding in diffSides"
```

---

### Task 3: Service 层 — 新增 getDetail 方法

**Files:**
- Modify: `src/main/java/com/tightening/service/ProductMissionService.java`

**Interfaces:**
- Produces: `ProductMissionDetailDTO getDetail(Long missionId)` — 返回包含 sides（含 Base64 图片）的完整详情

- [ ] **Step 1: 新增 getDetail 方法**

在 `ProductMissionService.java` 中 `listByPage` 方法后添加：

```java
    public ProductMissionDetailDTO getDetail(Long missionId) {
        ProductMission mission = getById(missionId);
        if (mission == null) return null;

        ProductMissionDetailDTO dto = Converter.entity2Dto(mission, ProductMissionDetailDTO::new);

        List<ProductSide> sides = sideService.lambdaQuery()
                .eq(ProductSide::getProductMissionId, missionId)
                .eq(ProductSide::getDeleted, 0)
                .list();

        List<ProductSideSaveItem> sideItems = new ArrayList<>();
        for (ProductSide side : sides) {
            ProductSideSaveItem item = Converter.entity2Dto(side, ProductSideSaveItem::new);
            item.setImage(encodeBase64(side.getImageData()));
            item.setRenderedImage(encodeBase64(side.getRenderedImageData()));
            item.setThumbnail(encodeBase64(side.getThumbnailData()));
            sideItems.add(item);
        }
        dto.setSides(sideItems);

        return dto;
    }

    private static String encodeBase64(byte[] data) {
        return data != null ? java.util.Base64.getEncoder().encodeToString(data) : null;
    }
```

- [ ] **Step 2: 运行编译检查**

```bash
cd /Users/streen/IdeaProjects/tightening && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/tightening/service/ProductMissionService.java
git commit -m "feat: add getDetail method with Base64-encoded side images"
```

---

### Task 4: Controller 层改造

**Files:**
- Modify: `src/main/java/com/tightening/controller/ProductMissionController.java`

**Interfaces:**
- Consumes: `ProductMissionDetailDTO` from `ProductMissionService`
- Changes: create/update 从 multipart 改为 `@RequestBody` JSON，get 返回 `ProductMissionDetailDTO`

- [ ] **Step 1: 重写 Controller**

`src/main/java/com/tightening/controller/ProductMissionController.java` 完整替换为：

```java
package com.tightening.controller;

import com.tightening.dto.ApiResponse;
import com.tightening.dto.BarCodeMatchingRuleDTO;
import com.tightening.dto.InspectionMissionBindingDTO;
import com.tightening.dto.MissionPrerequisiteDTO;
import com.tightening.dto.PageResult;
import com.tightening.dto.ProductMissionDTO;
import com.tightening.dto.ProductMissionDetailDTO;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.InspectionMissionBinding;
import com.tightening.entity.ProductMission;
import com.tightening.service.BarCodeMatchingRuleService;
import com.tightening.service.InspectionMissionBindingService;
import com.tightening.service.MissionPrerequisiteService;
import com.tightening.service.ProductMissionService;
import com.tightening.util.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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
@RequestMapping("api/missions")
@RequiredArgsConstructor
public class ProductMissionController {

    private final ProductMissionService missionService;
    private final MissionPrerequisiteService prerequisiteService;
    private final InspectionMissionBindingService bindingService;
    private final BarCodeMatchingRuleService barcodeRuleService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResult<ProductMissionDTO>>> list(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "100") int size,
                                                        @RequestParam(required = false) String name) {
        var resultPage = missionService.listByPage(page, size, name);
        var dtos = Converter.entity2Dto(resultPage.getRecords(), ProductMissionDTO::new);
        return ResponseEntity.ok(ApiResponse.ok(PageResult.of(resultPage, dtos)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductMissionDetailDTO>> get(@PathVariable Long id) {
        ProductMissionDetailDTO detail = missionService.getDetail(id);
        if (detail == null) return ResponseEntity.ok(ApiResponse.fail("not found"));
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    @GetMapping("/check-name")
    public ResponseEntity<ApiResponse<Boolean>> checkName(@RequestParam String name,
                                                           @RequestParam(required = false) Long excludeId) {
        boolean exists = missionService.isNameDuplicate(name, excludeId);
        return ResponseEntity.ok(ApiResponse.ok(exists));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductMissionDetailDTO>> create(@RequestBody ProductMissionDetailDTO dto) {
        try {
            ProductMissionDetailDTO result = missionService.saveMission(dto);
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (DuplicateKeyException e) {
            return ResponseEntity.ok(ApiResponse.fail("任务名称已存在"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.fail(e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Create mission failed", e);
            return ResponseEntity.ok(ApiResponse.fail("新增失败: " + unwrapCause(e)));
        } catch (Exception e) {
            log.error("Create mission failed", e);
            return ResponseEntity.ok(ApiResponse.fail("新增失败"));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductMissionDetailDTO>> update(@PathVariable Long id,
                                                                         @RequestBody ProductMissionDetailDTO dto) {
        try {
            dto.setId(id);
            ProductMissionDetailDTO result = missionService.saveMission(dto);
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (DuplicateKeyException e) {
            return ResponseEntity.ok(ApiResponse.fail("任务名称已存在"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.fail(e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Update mission failed: id={}", id, e);
            return ResponseEntity.ok(ApiResponse.fail("更新失败: " + unwrapCause(e)));
        } catch (Exception e) {
            log.error("Update mission failed: id={}", id, e);
            return ResponseEntity.ok(ApiResponse.fail("更新失败"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Long id) {
        missionService.cascadeDelete(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/{missionId}/prerequisites")
    public ResponseEntity<ApiResponse<List<MissionPrerequisiteDTO>>> listPrerequisites(@PathVariable Long missionId) {
        List<MissionPrerequisiteDTO> prerequisites = prerequisiteService.listByMissionId(missionId);
        return ResponseEntity.ok(ApiResponse.ok(prerequisites));
    }

    @GetMapping("/{missionId}/inspection-bindings")
    public ResponseEntity<ApiResponse<List<InspectionMissionBindingDTO>>> listInspectionBindings(@PathVariable Long missionId) {
        List<InspectionMissionBinding> bindings = bindingService.listByInspectionMissionId(missionId);
        return ResponseEntity.ok(ApiResponse.ok(Converter.entity2Dto(bindings, InspectionMissionBindingDTO::new)));
    }

    @GetMapping("/{missionId}/barcode-rules")
    public ResponseEntity<ApiResponse<List<BarCodeMatchingRuleDTO>>> listBarcodeRules(@PathVariable Long missionId) {
        List<BarCodeMatchingRule> rules = barcodeRuleService.listByMissionId(missionId);
        return ResponseEntity.ok(ApiResponse.ok(Converter.entity2Dto(rules, BarCodeMatchingRuleDTO::new)));
    }

    private static String unwrapCause(RuntimeException e) {
        return e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
    }
}
```

- [ ] **Step 2: 移除不再需要的 import**

已在上一步完整代码中体现：移除 `MultipartFile`、`MultipartHttpServletRequest`、`HttpServletRequest`、`HashMap`、`Map`、`MediaType`、`JsonUtils`。

- [ ] **Step 3: 运行编译检查**

```bash
cd /Users/streen/IdeaProjects/tightening && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/controller/ProductMissionController.java
git commit -m "refactor: switch mission create/update from multipart to JSON, add detail endpoint"
```

---

### Task 5: 删除 ProductSideController 和移除 ProductSideService 无用方法

**Files:**
- Delete: `src/main/java/com/tightening/controller/ProductSideController.java`
- Modify: `src/main/java/com/tightening/service/ProductSideService.java`

- [ ] **Step 1: 删除 ProductSideController**

```bash
rm /Users/streen/IdeaProjects/tightening/src/main/java/com/tightening/controller/ProductSideController.java
```

- [ ] **Step 2: 移除 ProductSideService 中无用的方法**

`src/main/java/com/tightening/service/ProductSideService.java` 变为：

```java
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

    public List<Long> listSideIdsByMissionId(Long missionId) {
        return lambdaQuery()
                .select(ProductSide::getId)
                .eq(ProductSide::getProductMissionId, missionId)
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
```

移除的方法：`getByIdWithoutBlobs`、`listByMissionId`、`getImageData`、`updateImageData`、`updateRenderedImageData`、`updateThumbnailData`。

移除对应的 import：`ImageType`、`ProductBolt`。

- [ ] **Step 3: 运行编译检查**

```bash
cd /Users/streen/IdeaProjects/tightening && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git rm src/main/java/com/tightening/controller/ProductSideController.java
git add src/main/java/com/tightening/service/ProductSideService.java
git commit -m "refactor: remove ProductSideController and unused ProductSideService methods"
```

---

### Task 6: 验证测试通过

**验证对象:**
- `src/test/java/com/tightening/entity/ProductSideTest.java` — 纯实体 Jackson 序列化测试，不依赖被删除的 Service 方法，无需修改
- `src/test/java/com/tightening/dto/ProductSideDTOTest.java` — DTO 序列化测试，无需修改

- [ ] **Step 1: 运行全部测试**

```bash
cd /Users/streen/IdeaProjects/tightening && mvn test -q
```
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 2: 提交**

```bash
git add -A
git commit -m "chore: verify all tests pass after side image Base64 refactor"
```
