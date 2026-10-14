-- Extensions and the financial schema are managed exclusively by Flyway.
CREATE TABLE platform_metadata (
    key VARCHAR(64) PRIMARY KEY,
    value VARCHAR(255) NOT NULL
);
INSERT INTO platform_metadata (key, value) VALUES ('mode', 'EDUCATIONAL_SIMULATOR');
