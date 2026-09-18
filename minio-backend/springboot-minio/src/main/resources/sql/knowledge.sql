-- Additive migration. Execute once in the existing application database.
CREATE TABLE IF NOT EXISTS knowledge_space (
  owner_id INT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS knowledge_document (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  owner_id INT NOT NULL,
  series_id VARCHAR(36) NOT NULL,
  title VARCHAR(160) NOT NULL,
  source_url VARCHAR(1000) NOT NULL,
  publisher VARCHAR(160) NOT NULL,
  source_version VARCHAR(80) NOT NULL,
  valid_from DATE NOT NULL,
  valid_until DATE NOT NULL,
  content TEXT NOT NULL,
  status VARCHAR(16) NOT NULL,
  revision INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX knowledge_owner_status (owner_id, status),
  INDEX knowledge_owner_series (owner_id, series_id)
) ENGINE=InnoDB;
