CREATE TABLE package_kind_images (
  resource_key varchar(20) PRIMARY KEY CHECK (resource_key IN ('PT','GROUP10','GROUP30','MEMBERSHIP')),
  content bytea NOT NULL,
  version varchar(36) NOT NULL
);
