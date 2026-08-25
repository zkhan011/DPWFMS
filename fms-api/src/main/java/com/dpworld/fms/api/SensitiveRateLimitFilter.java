package com.dpworld.fms.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Lightweight per-client guard for sensitive mutations; ingress rate limits remain recommended. */
@Component
public class SensitiveRateLimitFilter extends OncePerRequestFilter {
  private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
  private final int requestsPerMinute;

  public SensitiveRateLimitFilter(@Value("${dpwfms.security.sensitive-rate-per-minute:30}") int requestsPerMinute) {
    this.requestsPerMinute = requestsPerMinute;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return "GET".equals(request.getMethod()) || (!request.getRequestURI().startsWith("/api/automation")
        && !request.getRequestURI().startsWith("/api/workspace/plants"));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
    long minute = Instant.now().getEpochSecond() / 60;
    String key = request.getRemoteAddr() + ":" + minute;
    Window window = windows.compute(key, (ignored, old) -> old == null || old.minute() != minute
        ? new Window(minute, 1) : new Window(minute, old.requests() + 1));
    windows.keySet().removeIf(entry -> !entry.endsWith(":" + minute));
    if (window.requests() > requestsPerMinute) {
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.setContentType("application/problem+json");
      response.getWriter().write("{\"title\":\"Rate limit exceeded\",\"status\":429}");
      return;
    }
    chain.doFilter(request, response);
  }

  private record Window(long minute, int requests) {}
}
