CREATE TABLE profile_images (
  resource_key bigint PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  content bytea NOT NULL,
  version varchar(36) NOT NULL
);
