# Mission 配置 API 测试指南

> 适用版本: V1.0.8+  
> Base URL: `http://localhost:8080`  
> RESTful API 设计: GET 查询, POST 创建, PUT 更新, DELETE 删除

## 前置条件

```bash
# 启动服务
mvn spring-boot:run

# 验证服务可用（任意端点返回 200/404 即表示服务运行中）
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/missions
# 预期: 200
```

---

## 一、ProductMission — 任务配置

### 1.1 创建普通任务

```bash
curl -X POST http://localhost:8080/api/missions \
  -H "Content-Type: application/json" \
  -d '{
    "name": "发动机装配",
    "maxNgCount": 3,
    "passwordRequiredAfterNg": 2,
    "enabled": 1,
    "multiDeviceIndependent": 0,
    "skipScrew": 0,
    "isInspection": 0
  }'
# 预期: 200, body 返回任务 ID
```

### 1.2 创建第二个任务（后续做前置依赖测试用）

```bash
curl -X POST http://localhost:8080/api/missions \
  -H "Content-Type: application/json" \
  -d '{
    "name": "变速箱装配",
    "maxNgCount": 5,
    "enabled": 1,
    "isInspection": 0
  }'
# 预期: 200, body 返回 ID
```

### 1.3 创建点检任务

```bash
curl -X POST http://localhost:8080/api/missions \
  -H "Content-Type: application/json" \
  -d '{
    "name": "白班点检",
    "maxNgCount": 1,
    "enabled": 1,
    "isInspection": 1,
    "inspectionScope": 2
  }'
# 预期: 200, body 返回 ID
# inspectionScope: 1=ALL 2=CHOSEN
```

### 1.4 编辑任务（更新 name）

```bash
curl -X PUT http://localhost:8080/api/missions/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "发动机装配-改",
    "maxNgCount": 5,
    "enabled": 1,
    "isInspection": 0
  }'
# 预期: 200, body 返回 "1"
```

### 1.5 获取单个任务

```bash
curl http://localhost:8080/api/missions/1
# 预期: 200, JSON body 含完整字段，name="发动机装配-改"
```

### 1.6 任务列表（分页）

```bash
curl "http://localhost:8080/api/missions?page=1&size=10"
# 预期: 200, JSON 数组
```

### 1.7 删除任务（级联）

```bash
curl -X DELETE http://localhost:8080/api/missions/1
# 预期: 200, "ok"
# 级联: 其下 sides → bolts → bindings 全部删除
```

---

## 二、前置依赖

### 2.1 添加前置依赖（任务2 依赖 任务1 完成，SAME_TRACE）

```bash
curl -X POST http://localhost:8080/api/missions/2/prerequisites \
  -H "Content-Type: application/json" \
  -d '{"prerequisiteMissionId":1,"prerequisiteType":1}'
# 预期: 200, "ok"
# prerequisiteType: 1=SAME_TRACE 2=PARTS_TRACE 3=INSPECTION_CHAIN
```

### 2.2 查看前置依赖列表

```bash
curl http://localhost:8080/api/missions/2/prerequisites
# 预期: 200, JSON 数组
```

### 2.3 删除单条前置依赖

```bash
curl -X DELETE http://localhost:8080/api/missions/2/prerequisites/1
# 预期: 200, "ok"
```

### 2.4 循环依赖检测（应报错）

```bash
# 先建 A→B
curl -X POST http://localhost:8080/api/missions/1/prerequisites \
  -H "Content-Type: application/json" \
  -d '{"prerequisiteMissionId":2,"prerequisiteType":1}'
# 预期: 200

# 再建 B→A（形成环）
curl -X POST http://localhost:8080/api/missions/2/prerequisites \
  -H "Content-Type: application/json" \
  -d '{"prerequisiteMissionId":1,"prerequisiteType":1}'
# 预期: 500, "检测到循环依赖: mission 2 不能依赖自身"
```

### 2.5 前置类型约束检查（应报错）

```bash
# 普通任务尝试 INSPECTION_CHAIN(3) 指向另一个普通任务
curl -X POST http://localhost:8080/api/missions/1/prerequisites \
  -H "Content-Type: application/json" \
  -d '{"prerequisiteMissionId":2,"prerequisiteType":3}'
# 预期: 500, "INSPECTION_CHAIN 的前置任务必须是点检任务 (is_inspection=1)"
```

---

## 三、点检绑定

### 3.1 添加点检绑定

```bash
curl -X POST http://localhost:8080/api/missions/3/inspection-bindings \
  -H "Content-Type: application/json" \
  -d '{"boundMissionId":2}'
# 预期: 200, "ok"
```

### 3.2 点检绑定列表

```bash
curl http://localhost:8080/api/missions/3/inspection-bindings
# 预期: 200, JSON 数组
```

### 3.3 点检绑定约束检查（应报错）

```bash
# 点检绑定到另一个点检任务
curl -X POST http://localhost:8080/api/missions/3/inspection-bindings \
  -H "Content-Type: application/json" \
  -d '{"boundMissionId":3}'
# 预期: 500, "点检任务不能绑定到另一个点检任务"
```

---

## 四、条码规则

### 4.1 添加追溯码规则（PRODUCT_TRACE=1）

```bash
curl -X POST http://localhost:8080/api/missions/1/barcode-rules \
  -H "Content-Type: application/json" \
  -d '{
    "name": "追溯码规则",
    "productMissionId": 1,
    "ruleType": 1,
    "expectedLength": 10,
    "keyStartPosition": 6,
    "keyEndPosition": 9,
    "keyChar": "ABCD"
  }'
# 预期: 200
# ruleType: 1=PRODUCT_TRACE 2=PARTS_BARCODE
```

### 4.2 添加物料码规则（PARTS_BARCODE=2）

```bash
curl -X POST http://localhost:8080/api/missions/1/barcode-rules \
  -H "Content-Type: application/json" \
  -d '{
    "name": "物料码规则",
    "productMissionId": 1,
    "ruleType": 2,
    "partNumber": "PN-12345",
    "expectedLength": 8
  }'
# 预期: 200
```

### 4.3 追溯码唯一性检查（应报错）

```bash
# 同一 mission 再加一条 PRODUCT_TRACE
curl -X POST http://localhost:8080/api/missions/1/barcode-rules \
  -H "Content-Type: application/json" \
  -d '{
    "name": "第二个追溯码",
    "productMissionId": 1,
    "ruleType": 1
  }'
# 预期: 500, "该 mission 已存在 PRODUCT_TRACE 规则，每个 mission 最多一条"
```

### 4.4 key_char 长度校验（应报错）

```bash
curl -X POST http://localhost:8080/api/missions/2/barcode-rules \
  -H "Content-Type: application/json" \
  -d '{
    "name": "错误规则",
    "productMissionId": 2,
    "ruleType": 1,
    "keyStartPosition": 6,
    "keyEndPosition": 9,
    "keyChar": "AB"
  }'
# 预期: 500, "key_char 长度(2)与位置范围(4)不匹配"
```

### 4.5 条码规则列表

```bash
curl http://localhost:8080/api/missions/1/barcode-rules
# 预期: 200, JSON 数组
```

### 4.6 删除条码规则

```bash
curl -X DELETE http://localhost:8080/api/missions/1/barcode-rules/1
# 预期: 200, "ok"
```

---

## 五、ProductSide — 产品面

### 5.1 创建面

```bash
curl -X POST http://localhost:8080/api/sides \
  -H "Content-Type: application/json" \
  -d '{
    "name": "A面",
    "productMissionId": 1
  }'
# 预期: 200, body 返回面 ID
```

### 5.2 获取面

```bash
curl http://localhost:8080/api/sides/1
# 预期: 200, JSON（不含 BLOB 字段）
```

### 5.3 面列表

```bash
curl "http://localhost:8080/api/sides?missionId=1"
# 预期: 200, JSON 数组
```

### 5.4 上传原图

```bash
curl -X PUT http://localhost:8080/api/sides/1/image \
  -F "file=@/path/to/test.png"
# 预期: 200, "ok"
# 支持: PNG/JPEG/GIF/WebP，≤5MB
```

### 5.5 上传成品图

```bash
curl -X PUT http://localhost:8080/api/sides/1/image/rendered \
  -F "file=@/path/to/rendered.png"
# 预期: 200, "ok"
```

### 5.6 上传缩略图

```bash
curl -X PUT http://localhost:8080/api/sides/1/image/thumbnail \
  -F "file=@/path/to/thumb.png"
# 预期: 200, "ok"
```

### 5.7 获取图片

```bash
# 成品图（默认）
curl "http://localhost:8080/api/sides/1/image?type=rendered" \
  -o rendered.png
# 预期: 200, 二进制图片

# 原图
curl "http://localhost:8080/api/sides/1/image?type=original" \
  -o original.png
# 预期: 200 或 404（未上传）

# 缩略图
curl "http://localhost:8080/api/sides/1/image?type=thumbnail" \
  -o thumb.png
# 预期: 200 或 404（未上传）
```

### 5.8 图片大小限制（应报错）

```bash
# 创建一个 6MB 的文件测试
dd if=/dev/zero of=/tmp/big.png bs=1m count=6
curl -X PUT http://localhost:8080/api/sides/1/image \
  -F "file=@/tmp/big.png"
# 预期: 400, "图片大小超过 5MB 限制"
```

### 5.9 文件类型限制（应报错）

```bash
echo "not an image" > /tmp/not-image.txt
curl -X PUT http://localhost:8080/api/sides/1/image \
  -F "file=@/tmp/not-image.txt"
# 预期: 400, "不支持的文件类型"
```

### 5.10 404 场景

```bash
curl "http://localhost:8080/api/sides/99999/image?type=rendered"
# 预期: 404
```

### 5.11 删除面（级联）

```bash
curl -X DELETE http://localhost:8080/api/sides/1
# 预期: 200, "ok"
# 级联: 其下 bolts → bindings 全部删除
```

---

## 六、ProductBolt — 螺栓点位

### 6.1 创建螺栓

```bash
curl -X POST "http://localhost:8080/api/bolts?missionId=1" \
  -H "Content-Type: application/json" \
  -d '{
    "productSideId": 1,
    "boltSerialNum": 1,
    "boltName": "螺栓-01",
    "parameterSetId": 10,
    "torqueMin": 5.0,
    "torqueMax": 25.0,
    "angleMin": 10.0,
    "angleMax": 180.0,
    "armLocation": "L",
    "locationXPercent": 30.5,
    "locationYPercent": 45.2,
    "enabled": 1
  }'
# 预期: 200, body 返回螺栓 ID
```

### 6.2 序列号唯一性检查（应报错）

```bash
# 同 mission 下重复 boltSerialNum
curl -X POST "http://localhost:8080/api/bolts?missionId=1" \
  -H "Content-Type: application/json" \
  -d '{
    "productSideId": 1,
    "boltSerialNum": 1
  }'
# 预期: 500, "bolt_serial_num 1 在当前 mission 中已存在"
```

### 6.3 螺栓列表

```bash
curl "http://localhost:8080/api/bolts?sideId=1"
# 预期: 200, JSON 数组
```

### 6.4 删除螺栓（级联）

```bash
curl -X DELETE http://localhost:8080/api/bolts/1
# 预期: 200, "ok"
# 级联: 其下 device_bindings + parts_barcodes 全部删除
```

---

## 七、完整集成测试脚本

```bash
#!/bin/bash
BASE="http://localhost:8080"
CT="Content-Type: application/json"
PASS=0; FAIL=0

check() {
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" -X "$2" "$3" -H "$CT" ${4:+-d "$4"})
  if [ "$code" = "$1" ]; then echo "  PASS  $1 $2 $3"; ((PASS++)); else echo "  FAIL  $1 $2 $3 (got $code)"; ((FAIL++)); fi
}
create() {
  resp=$(curl -s -w "\n%{http_code}" -X "$1" "$2" -H "$CT" -d "$3")
  code=$(echo "$resp" | tail -1)
  body=$(echo "$resp" | sed '$d')
  if [ "$code" != "200" ]; then echo "  FAIL  CREATE $1 $2 (got $code)">&2; ((FAIL++)); fi
  echo "$body"
}

echo "=== 1. Mission CRUD ==="
MID1=$(create POST "$BASE/api/missions" '{"name":"集成测试","maxNgCount":3,"enabled":1,"isInspection":0}')
MID2=$(create POST "$BASE/api/missions" '{"name":"集成测试2","maxNgCount":5,"enabled":1,"isInspection":0}')
MID3=$(create POST "$BASE/api/missions" '{"name":"点检","maxNgCount":1,"enabled":1,"isInspection":1,"inspectionScope":2}')

check 200 GET "$BASE/api/missions/$MID1"
check 200 GET "$BASE/api/missions?page=1&size=10"
check 200 PUT "$BASE/api/missions/$MID1" '{"name":"集成测试-改","maxNgCount":5,"enabled":1,"isInspection":0}'

echo "=== 2. 前置依赖 ==="
check 200 POST "$BASE/api/missions/$MID2/prerequisites" "{\"prerequisiteMissionId\":$MID1,\"prerequisiteType\":1}"
check 500 POST "$BASE/api/missions/$MID1/prerequisites" "{\"prerequisiteMissionId\":$MID2,\"prerequisiteType\":1}"

echo "=== 3. 点检绑定 ==="
check 200 POST "$BASE/api/missions/$MID3/inspection-bindings" "{\"boundMissionId\":$MID2}"
check 500 POST "$BASE/api/missions/$MID3/inspection-bindings" "{\"boundMissionId\":$MID3}"

echo "=== 4. Side CRUD ==="
SID=$(create POST "$BASE/api/sides" "{\"name\":\"测试面\",\"productMissionId\":$MID1}")
check 200 GET "$BASE/api/sides/$SID"
check 200 GET "$BASE/api/sides?missionId=$MID1"
check 404 GET "$BASE/api/sides/99999/image?type=rendered"

echo "=== 5. Bolt CRUD ==="
BID=$(create POST "$BASE/api/bolts?missionId=$MID1" "{\"productSideId\":$SID,\"boltSerialNum\":1,\"torqueMin\":5.0,\"torqueMax\":25.0}")
check 500 POST "$BASE/api/bolts?missionId=$MID1" "{\"productSideId\":$SID,\"boltSerialNum\":1}"
check 200 GET "$BASE/api/bolts?sideId=$SID"

echo "=== 6. 条码规则 ==="
RID=$(create POST "$BASE/api/missions/$MID1/barcode-rules" "{\"name\":\"追溯码\",\"productMissionId\":$MID1,\"ruleType\":1}")
check 500 POST "$BASE/api/missions/$MID1/barcode-rules" "{\"name\":\"第二个\",\"productMissionId\":$MID1,\"ruleType\":1}"

echo "=== 7. 级联删除 ==="
check 200 DELETE "$BASE/api/bolts/$BID"
check 200 DELETE "$BASE/api/sides/$SID"
check 200 DELETE "$BASE/api/missions/$MID1"
check 200 DELETE "$BASE/api/missions/$MID2"
check 200 DELETE "$BASE/api/missions/$MID3"

echo ""
echo "=== $PASS 通过, $FAIL 失败 ==="
```

---

## 枚举值速查

| 枚举 | 值 |
|------|-----|
| PrerequisiteType | SAME_TRACE=1, PARTS_TRACE=2, INSPECTION_CHAIN=3 |
| InspectionScope | ALL=1, CHOSEN=2 |
| IoDeviceType | ARRANGER=1, SETTER_SELECTOR=2 |
| BarCodeRuleType | PRODUCT_TRACE=1, PARTS_BARCODE=2 |
| ImageType | original, rendered, thumbnail（字符串，非整数） |
