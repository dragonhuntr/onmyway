-- Restore Bruno's original hours. NULL means open until midnight campus time.
UPDATE restaurants SET closes_min = NULL WHERE id = 'brunos';
