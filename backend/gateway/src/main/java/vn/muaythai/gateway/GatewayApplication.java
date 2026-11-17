package vn.muaythai.gateway;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

@SpringBootApplication
public class GatewayApplication {
  public static void main(String[] args) {
    SpringApplication.run(GatewayApplication.class, args);
  }

  @Bean
  RouteLocator routes(RouteLocatorBuilder routes, SessionFilter session,
      @Value("${app.auth-uri}") String authUri, @Value("${APP_SCHEDULE_URI}") String schedule,
      @Value("${APP_CHECKIN_URI}") String checkin, @Value("${APP_PROGRESS_URI}") String progress) {
    return routes.routes()
        .route("auth", route -> route.path("/api/auth/**", "/api/users/**", "/api/learners/**", "/api/trainers/**")
            .filters(filters -> filters.removeRequestHeader("X-Service-Key")).uri(authUri))
        .route("calendar-events", route -> route.path("/api/sessions/events")
            .filters(filters -> filters.filter(session)).metadata("response-timeout", -1).uri(schedule))
        .route("schedule", route -> route.path("/api/classes30/**", "/api/classes10/**", "/api/sessions/**",
            "/api/pt/**", "/api/shifts/**").filters(filters -> filters.filter(session)).uri(schedule))
        .route("checkin", route -> route.path("/api/packages/**", "/api/checkins/**", "/api/care/**")
            .filters(filters -> filters.filter(session)).uri(checkin))
        .route("progress", route -> route.path("/api/notes/**", "/api/goals/**", "/api/notifications/**")
            .filters(filters -> filters.filter(session)).uri(progress))
        .build();
  }

  @Bean
  ReactiveJwtDecoder decoder(@Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.issuer}") String issuer) {
    NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(
        new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
    return decoder;
  }

  @Bean
  SecurityWebFilterChain security(ServerHttpSecurity http) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .authorizeExchange(auth -> auth
            .pathMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
            .pathMatchers(HttpMethod.GET, "/actuator/health").permitAll()
            .pathMatchers("/api/**").authenticated()
            .anyExchange().denyAll())
        .oauth2ResourceServer(resource -> resource.jwt(jwt -> {}))
        .build();
  }
}
