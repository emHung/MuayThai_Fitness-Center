package vn.muaythai.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.bootstrap-admin.enabled", havingValue = "true")
class BootstrapAdmin implements ApplicationRunner {
  private final JdbcClient database;
  private final UserAccounts users;
  private final PasswordEncoder passwords;
  private final String email;
  private final String temporaryPassword;

  BootstrapAdmin(JdbcClient database, UserAccounts users, PasswordEncoder passwords,
      @Value("${app.bootstrap-admin.email:}") String email,
      @Value("${app.bootstrap-admin.password:}") String temporaryPassword) {
    this.database = database;
    this.users = users;
    this.passwords = passwords;
    this.email = email.strip().toLowerCase(Locale.ROOT);
    this.temporaryPassword = temporaryPassword;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    database.sql("select pg_advisory_xact_lock(13659147)").query((rs, row) -> true).single();
    if (database.sql("select exists (select from users where role='ADMIN')").query(Boolean.class).single()) {
      return;
    }
    if (email.length() > 120 || !email.matches("[^\\s@]++@[^\\s@.]++(?:\\.[^\\s@.]++)++")
        || temporaryPassword.isBlank() || temporaryPassword.length() < 16
        || temporaryPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new IllegalStateException("Set a valid bootstrap email and a temporary password of 16 to 72 bytes.");
    }
    UserAccount admin = new UserAccount(email, "Quản lý", passwords.encode(temporaryPassword),
        UserAccount.Role.ADMIN, true);
    admin.canAdjustCheckins = true;
    users.save(admin);
  }
}
