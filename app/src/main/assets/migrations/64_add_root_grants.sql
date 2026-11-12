CREATE TABLE IF NOT EXISTS `root_grants` (
  `id` TEXT NOT NULL,
  `realPath` TEXT NOT NULL,
  `operation` TEXT NOT NULL,
  `scope` TEXT NOT NULL,
  `createdAt` INTEGER NOT NULL,
  `revocable` INTEGER NOT NULL,
  PRIMARY KEY(`id`)
);

CREATE UNIQUE INDEX IF NOT EXISTS `index_root_grants_realPath_operation`
ON `root_grants` (`realPath`, `operation`);
