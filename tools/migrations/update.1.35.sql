ALTER TABLE `users`
	ADD COLUMN `online` tinyint(1) NOT NULL DEFAULT 0 AFTER `last_online`;
