-- No CHECK constraint on purpose: the app validates the value, so a new algorithm needs no migration.
ALTER TABLE rules ADD COLUMN algorithm VARCHAR(32) NOT NULL DEFAULT 'fixed_window';
