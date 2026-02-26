-- ----------------------------
-- Table structure for device_arm
-- ----------------------------
DROP TABLE IF EXISTS "device";
CREATE TABLE "device" (
  "id" integer NOT NULL PRIMARY KEY AUTOINCREMENT,
  "name" text(128),
  "description" text(512),
  "type" integer(4),
  "detail" text(1024),
  "deleted" integer(1) NOT NULL DEFAULT 0,
  "creator_id" integer NOT NULL,
  "modifier_id" integer NOT NULL,
  "create_time" text(64) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "modify_time" text(64) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
