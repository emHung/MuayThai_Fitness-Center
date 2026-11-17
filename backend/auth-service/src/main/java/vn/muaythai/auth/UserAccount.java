package vn.muaythai.auth;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
class UserAccount {
  enum Role {
    ADMIN,
    TRAINER,
    LEARNER
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  String username;
  String displayName;
  String passwordHash;

  @Enumerated(EnumType.STRING)
  Role role;

  boolean canAdjustCheckins;
  boolean locked;
  boolean mustChangePassword;
  Instant deletedAt;

  protected UserAccount() {}

  UserAccount(String username, String displayName, String passwordHash, Role role, boolean temporary) {
    this.username = username;
    this.displayName = displayName;
    this.passwordHash = passwordHash;
    this.role = role;
    this.mustChangePassword = temporary;
  }
}
