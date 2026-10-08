package com.luokuiai.forga.spring;

import java.util.List;
import java.util.Objects;
import org.springframework.web.util.pattern.PathPatternParser;

/** Path scope for Starter-managed Spring MVC endpoint permission enforcement. */
public record ForgaWebScope(List<String> includePaths, List<String> excludePaths) {

  /**
   * Creates a validated path scope.
   *
   * @param includePaths included MVC paths; at least one is required
   * @param excludePaths paths excluded from enforcement
   */
  public ForgaWebScope {
    includePaths = validate(includePaths, true);
    excludePaths = validate(excludePaths, false);
  }

  /**
   * Includes the supplied paths without exclusions.
   *
   * @param includePaths MVC path patterns
   * @return configured scope
   */
  public static ForgaWebScope include(String... includePaths) {
    return new ForgaWebScope(List.of(includePaths), List.of());
  }

  private static List<String> validate(List<String> paths, boolean required) {
    List<String> result = List.copyOf(Objects.requireNonNull(paths, "paths are required"));
    if (required && result.isEmpty()) {
      throw new IllegalArgumentException("at least one Forga Web include path is required");
    }
    for (String path : result) {
      if (path.isBlank() || !path.startsWith("/")) {
        throw new IllegalArgumentException("invalid Forga Web path pattern: " + path);
      }
      PathPatternParser.defaultInstance.parse(path);
    }
    return result;
  }
}
