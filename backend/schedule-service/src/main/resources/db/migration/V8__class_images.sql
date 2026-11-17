CREATE TABLE class_images (
  resource_key bigint PRIMARY KEY REFERENCES classes(id) ON DELETE CASCADE,
  content bytea NOT NULL,
  version varchar(36) NOT NULL
);
