# TighteningData 采用快照存储，不与上游实体强关联

TighteningData 中冗余存储 `workstationName`、`productSideName`、`boltSerialNum` 等业务字段，而非仅通过 `missionRecordId` 关联上游实体。

**理由**: ProductMission、ProductSide 等上游实体可能被修改或删除。TighteningData 作为已发生的拧紧历史记录，必须自包含、可独立解读。通过外键或逻辑 ID 关联会在上游数据变更时导致历史记录断裂或语义丢失。

**权衡**: 牺牲了数据规范性和引用完整性，换取了历史数据的长期可追溯性。
