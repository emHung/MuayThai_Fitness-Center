package vn.muaythai.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProfileService {
  private static final RowMapper<ProfileApi.Profile> PROFILE = (rs, row) -> new ProfileApi.Profile(
      rs.getLong("id"), rs.getString("username"), rs.getString("display_name"), rs.getString("role"),
      rs.getBoolean("locked") ? "LOCKED" : "ACTIVE", rs.getString("member_code"),
      rs.getString("phone"), rs.getString("specialty"));

  private final JdbcClient db;
  private final PasswordEncoder passwords;
  private final InternalServices internal;
  private final RestClient checkin;
  private final SecureRandom random = new SecureRandom();

  ProfileService(JdbcClient db, PasswordEncoder passwords, InternalServices internal,
      @Value("${app.checkin-uri:http://checkin:8080}") String checkinUri) {
    this.db = db;
    this.passwords = passwords;
    this.internal = internal;
    checkin = internal.client(checkinUri);
  }

  ProfileApi.Page list(AuthApi.User actor, UserAccount.Role role, String q, int page, int size) {
    List<Long> allowed = new ArrayList<>(allowedLearners(actor, role));
    allowed.add(-1L);
    String filter = """
        FROM users WHERE role = :role AND deleted_at IS NULL AND (:admin OR id IN (:allowed))
          AND (strpos(lower(display_name), :q) > 0
          OR strpos(username, :q) > 0 OR strpos(lower(coalesce(member_code, '')), :q) > 0
          OR strpos(coalesce(phone, ''), :q) > 0)
        """;
    String query = q.strip().toLowerCase(Locale.ROOT);
    long total = db.sql("SELECT count(*) " + filter).param("role", role.name()).param("q", query).param("admin", actor.role() == UserAccount.Role.ADMIN).param("allowed", allowed)
        .query(Long.class).single();
    List<ProfileApi.Profile> items = db.sql("SELECT * " + filter + " ORDER BY display_name, id LIMIT :size OFFSET :offset")
        .param("role", role.name()).param("q", query).param("admin", actor.role() == UserAccount.Role.ADMIN).param("allowed", allowed).param("size", size).param("offset", (long) page * size)
        .query(PROFILE).list();
    return new ProfileApi.Page(items, page, size, total);
  }

  ProfileApi.Profile detail(AuthApi.User actor, UserAccount.Role role, long id) {
    if (actor.role() != UserAccount.Role.ADMIN && !allowedLearners(actor, role).contains(id)) {
      throw new ApiError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền xem hồ sơ này.");
    }
    return typed(id, role);
  }

  @Transactional
  public ProfileApi.Created create(AuthApi.User actor, UserAccount.Role role, ProfileApi.Create form) {
    requireAdmin(actor);
    String password = form.temporaryPassword();
    if (password == null) {
      byte[] bytes = new byte[18];
      random.nextBytes(bytes);
      password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    } else if (role != UserAccount.Role.TRAINER || password.isBlank()
        || password.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new ApiError(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "Mật khẩu tạm của HLV phải hợp lệ và tối đa 72 byte.");
    }
    try {
      ProfileApi.Profile profile = db.sql("""
          INSERT INTO users (username, display_name, password_hash, role, must_change_password,
            member_code, phone, specialty)
          VALUES (:username, :name, :password, :role, :temporary,
            CASE WHEN :role = 'LEARNER' THEN 'HV' || nextval('learner_code_seq') ELSE NULL END, :phone, :specialty)
          RETURNING *
          """)
          .param("username", form.username().strip().toLowerCase(Locale.ROOT))
          .param("name", form.displayName().strip()).param("password", passwords.encode(password))
          .param("role", role.name()).param("temporary", role == UserAccount.Role.TRAINER)
          .param("phone", optional(form.phone())).param("specialty", role == UserAccount.Role.TRAINER
              ? optional(form.specialty()) : null).query(PROFILE).single();
      return new ProfileApi.Created(profile, password);
    } catch (DuplicateKeyException conflict) {
      throw new ApiError(HttpStatus.CONFLICT, "USERNAME_EXISTS", "Email này đã được sử dụng.");
    }
  }

  ProfileApi.Profile edit(AuthApi.User actor, UserAccount.Role role, long id, ProfileApi.Edit form) {
    requireAdmin(actor);
    return save(id, role, form);
  }

  ProfileApi.Profile editSelf(AuthApi.User actor, ProfileApi.Edit form) {
    return save(actor.id(), actor.role(), form);
  }

  ProfileApi.Profile internalUserForSelf(AuthApi.User actor) {
    return typed(actor.id(), actor.role());
  }

  ProfileApi.Profile internalUser(String key, long id) {
    requireInternal(key);
    return db.sql("SELECT * FROM users WHERE id = :id AND deleted_at IS NULL").param("id", id)
        .query(PROFILE).optional().orElseThrow(ProfileService::notFound);
  }

  ProfileApi.Profile internalMember(String key, String code) {
    requireInternal(key);
    return db.sql("SELECT * FROM users WHERE member_code = :code")
        .param("code", code.strip().toUpperCase(Locale.ROOT))
        .query(PROFILE).optional().orElseThrow(ProfileService::notFound);
  }

  private ProfileApi.Profile save(long id, UserAccount.Role role, ProfileApi.Edit form) {
    return db.sql("""
        UPDATE users SET display_name = :name, phone = :phone, specialty = :specialty
        WHERE id = :id AND role = :role AND deleted_at IS NULL RETURNING *
        """)
        .param("name", form.displayName().strip()).param("phone", optional(form.phone()))
        .param("specialty", role == UserAccount.Role.TRAINER ? optional(form.specialty()) : null)
        .param("id", id).param("role", role.name()).query(PROFILE).optional().orElseThrow(ProfileService::notFound);
  }

  private ProfileApi.Profile typed(long id, UserAccount.Role role) {
    return db.sql("SELECT * FROM users WHERE id = :id AND role = :role AND deleted_at IS NULL")
        .param("id", id).param("role", role.name()).query(PROFILE).optional().orElseThrow(ProfileService::notFound);
  }

  private static String optional(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private List<Long> allowedLearners(AuthApi.User actor, UserAccount.Role role) {
    if (actor.role() == UserAccount.Role.ADMIN) {
      return List.of();
    }
    if (actor.role() != UserAccount.Role.TRAINER || role != UserAccount.Role.LEARNER) {
      requireAdmin(actor);
    }
    try {
      Long[] ids = checkin.get().uri("/internal/assignments?trainerId={id}", actor.id())
          .retrieve().body(Long[].class);
      return ids == null ? List.of() : Arrays.asList(ids);
    } catch (RestClientException unavailable) {
      throw new ApiError(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", "Chưa thể tải học viên PT.");
    }
  }

  static void requireAdmin(AuthApi.User actor) {
    if (actor.role() != UserAccount.Role.ADMIN) {
      throw new ApiError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền quản lý hồ sơ này.");
    }
  }

  void requireInternal(String key) {
    internal.requireKey(key);
  }

  static ApiError notFound() {
    return new ApiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Không tìm thấy hồ sơ.");
  }
}
