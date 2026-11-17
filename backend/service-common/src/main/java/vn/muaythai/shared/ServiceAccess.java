package vn.muaythai.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
class ServiceAccess extends OncePerRequestFilter {
  static final String ATTRIBUTE = Actor.class.getName();
  private final byte[] key;

  ServiceAccess(@Value("${app.internal-key}") String key) {
    if (key.length() < 32) {
      throw new IllegalArgumentException("An internal service key is required.");
    }
    this.key = key.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !path.startsWith("/api/") && !path.startsWith("/internal/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    String supplied = request.getHeader("X-Service-Key");
    if (supplied == null || !MessageDigest.isEqual(key, supplied.getBytes(StandardCharsets.UTF_8))) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    if (request.getRequestURI().startsWith("/api/")) {
      Actor actor = extractActor(request);
      if (actor == null) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
        return;
      }
      request.setAttribute(ATTRIBUTE, actor);
    }
    chain.doFilter(request, response);
  }

  private static Actor extractActor(HttpServletRequest request) {
    String id = request.getHeader("X-Actor-Id");
    String role = request.getHeader("X-Actor-Role");
    if (id == null || !id.matches("[1-9][0-9]{0,17}") || role == null
        || !Set.of("ADMIN", "TRAINER", "LEARNER").contains(role)) {
      return null;
    }
    return new Actor(Long.parseLong(id), role, "true".equals(request.getHeader("X-Actor-Adjust")));
  }
}

@Configuration
class ActorResolver implements WebMvcConfigurer {
  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new HandlerMethodArgumentResolver() {
      @Override
      public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == Actor.class;
      }

      @Override
      public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
          NativeWebRequest request, WebDataBinderFactory binder) {
        Actor actor = (Actor) request.getAttribute(ServiceAccess.ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);
        if (actor == null) {
          throw Problem.forbidden();
        }
        return actor;
      }
    });
  }
}
