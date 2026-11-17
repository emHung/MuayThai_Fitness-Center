package vn.muaythai.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
class DemoAccounts implements ApplicationRunner {
  private final UserAccounts users;
  private final PasswordEncoder encoder;
  private final String adminPassword;
  private final String trainerPassword;
  private final String learnerPassword;

  DemoAccounts(UserAccounts users, PasswordEncoder encoder,
      @Value("${app.demo.admin-password}") String adminPassword,
      @Value("${app.demo.trainer-password}") String trainerPassword,
      @Value("${app.demo.learner-password}") String learnerPassword) {
    this.users = users;
    this.encoder = encoder;
    this.adminPassword = adminPassword;
    this.trainerPassword = trainerPassword;
    this.learnerPassword = learnerPassword;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments arguments) {
    create("admin@example.test", "Chị Mẫn", UserAccount.Role.ADMIN, adminPassword, false);
    create("trainer@example.test", "Anh Bảo", UserAccount.Role.TRAINER, trainerPassword, true);
    create("learner@example.test", "Nguyễn Văn An", UserAccount.Role.LEARNER, learnerPassword, false);
  }

  private void create(String email, String name, UserAccount.Role role, String password, boolean temporary) {
    if (password.length() < 8) {
      throw new IllegalArgumentException("Demo accounts require non-empty passwords of at least 8 characters.");
    }
    if (!users.existsByUsername(email)) {
      UserAccount user = new UserAccount(email, name, encoder.encode(password), role, temporary);
      user.canAdjustCheckins = role == UserAccount.Role.ADMIN;
      users.save(user);
    }
  }
}
