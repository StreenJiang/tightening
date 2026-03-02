-- ----------------------------
-- Table structure for user_account_info
-- ----------------------------
DROP TABLE IF EXISTS "user_account_info";
CREATE TABLE "user_account_info" (
  "id" integer NOT NULL PRIMARY KEY AUTOINCREMENT,
  "staff_id" text(128) NOT NULL,
  "name" text(128) NOT NULL,
  "position" text(128),
  "account" text(64) NOT NULL,
  "password" text(64),
  "operation_password" text(64),
  "deleted" integer(1) NOT NULL DEFAULT 0,
  "creator_id" integer NOT NULL,
  "modifier_id" integer NOT NULL,
  "create_time" text(64) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "modify_time" text(64) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ----------------------------
-- Records of user_account_info
-- ----------------------------
INSERT INTO "user_account_info" ("staff_id", "name", "position", "account", "password", "operation_password", "creator_id", "modifier_id") VALUES (-1, 'Developr', NULL, 'sys', '8BA05BCA959209F6CC8C4409C66E2CB5', '8BA05BCA959209F6CC8C4409C66E2CB5', 1, 1);
INSERT INTO "user_account_info" ("staff_id", "name", "position", "account", "password", "operation_password", "creator_id", "modifier_id") VALUES (-2, 'Admin', NULL, 'admin', '21232F297A57A5A743894A0E4A801FC3', '21232F297A57A5A743894A0E4A801FC3', 1, 1);
