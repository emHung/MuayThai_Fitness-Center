package vn.muaythai.auth;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface UserAccounts extends JpaRepository<UserAccount, Long> {
  boolean existsByUsername(String username);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from UserAccount u where u.username = :username")
  Optional<UserAccount> lockByUsername(String username);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from UserAccount u where u.id = :id")
  Optional<UserAccount> lockById(Long id);
}
