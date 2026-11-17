package vn.muaythai.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
final class SessionFilter implements GatewayFilter {
  private static final byte[] UNAVAILABLE = """
      {"code":"DEPENDENCY_UNAVAILABLE","message":"Dịch vụ xác thực đang bận. Vui lòng thử lại."}
      """.getBytes(StandardCharsets.UTF_8);

  private final WebClient auth;
  private final String key;

  SessionFilter(WebClient.Builder builder, @Value("${app.auth-uri}") String auth,
      @Value("${APP_INTERNAL_KEY}") String key) {
    this.auth = builder.baseUrl(auth).build();
    this.key = key;
  }

  private record State(HttpStatusCode status, JsonNode actor, byte[] error) {}

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    return auth.get().uri("/api/users/me").header(HttpHeaders.AUTHORIZATION, authorization)
        .exchangeToMono(response -> response.statusCode().is2xxSuccessful()
            ? response.bodyToMono(JsonNode.class).map(user -> new State(response.statusCode(), user, null))
            : response.bodyToMono(byte[].class).defaultIfEmpty(new byte[0])
                .map(error -> new State(response.statusCode(), null, error)))
        .timeout(Duration.ofSeconds(3))
        .onErrorReturn(new State(HttpStatus.SERVICE_UNAVAILABLE, null, UNAVAILABLE))
        .flatMap(state -> forward(exchange, chain, state));
  }

  private Mono<Void> forward(ServerWebExchange exchange, GatewayFilterChain chain, State state) {
    if (state.actor() == null) {
      exchange.getResponse().setStatusCode(state.status());
      exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
      return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(state.error())));
    }
    var actor = state.actor();
    var request = exchange.getRequest().mutate().headers(headers -> {
      headers.remove(HttpHeaders.AUTHORIZATION);
      headers.remove("X-Actor-Name");
      headers.set("X-Service-Key", key);
      headers.set("X-Actor-Id", actor.path("id").asText());
      headers.set("X-Actor-Role", actor.path("role").asText());
      headers.set("X-Actor-Adjust", actor.path("canAdjustCheckins").asBoolean() ? "true" : "false");
    }).build();
    return chain.filter(exchange.mutate().request(request).build());
  }
}
