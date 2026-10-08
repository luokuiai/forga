package com.luokuiai.forga.spring;

import com.luokuiai.forga.core.catalog.PermissionCatalogContributor;
import com.luokuiai.forga.spring.web.EndpointPermissionAuthorizer;
import com.luokuiai.forga.spring.web.EndpointPermissionContributor;
import com.luokuiai.forga.spring.web.EndpointPermissionRegistrations;
import com.luokuiai.forga.spring.web.EndpointPermissionRequirement;
import com.luokuiai.forga.spring.web.EndpointPermissionResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/** Spring Boot assembly for externally registered Spring MVC endpoint permissions. */
@AutoConfiguration
@AutoConfigureBefore(ForgaPermissionCatalogAutoConfiguration.class)
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnForgaEnabled
public class ForgaSpringWebAutoConfiguration {

  /**
   * Default enforcement scope, used when the host does not provide one.
   *
   * @return all MVC paths
   */
  @Bean
  @ConditionalOnMissingBean(ForgaWebScope.class)
  public ForgaWebScope forgaWebScope() {
    return ForgaWebScope.include("/**");
  }

  /**
   * Assembles immutable endpoint registrations from host contributors.
   *
   * @param contributors endpoint permission contributors
   * @return immutable endpoint registrations
   */
  @Bean
  @ConditionalOnMissingBean
  public EndpointPermissionRegistrations forgaEndpointPermissionRegistrations(
      ObjectProvider<EndpointPermissionContributor> contributors) {
    return EndpointPermissionRegistrations.fromContributors(
        contributors.orderedStream().toList());
  }

  /**
   * Adapts endpoint permission definitions into the ordinary permission catalog.
   *
   * @param registrations assembled endpoint registrations
   * @return permission catalog contributor
   */
  @Bean("forgaEndpointPermissionCatalogContributor")
  public PermissionCatalogContributor forgaEndpointPermissionCatalogContributor(
      EndpointPermissionRegistrations registrations) {
    return registrations::definitions;
  }

  @Bean
  EndpointPermissionResolverDelegate forgaEndpointPermissionResolverDelegate() {
    return new EndpointPermissionResolverDelegate();
  }

  @Bean
  SmartInitializingSingleton forgaEndpointPermissionResolverCompilation(
      EndpointPermissionRegistrations registrations,
      ObjectProvider<EndpointPermissionResolver> hostResolvers,
      @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
      ObjectProvider<ForgaWebScope> scopes,
      EndpointPermissionResolverDelegate delegate) {
    return () -> {
      ForgaWebScope scope = resolveScope(scopes);
      var mappings = handlerMapping.getHandlerMethods();
      EndpointPermissionResolver staticResolver =
          registrations.compile(mappings.values(), List.of());
      mappings.forEach(
          (mapping, handler) -> {
            if (staticResolver.resolve(handler, null).isPresent()) {
              validateScope(mapping, handler, scope);
            }
          });
      delegate.initialize(
          registrations.compile(mappings.values(), hostResolvers.orderedStream().toList()));
    };
  }

  @Bean
  SmartInitializingSingleton forgaEndpointPermissionEnforcementValidation(
      ObjectProvider<EndpointPermissionAuthorizer> authorizer) {
    return () -> {
      if (authorizer.getIfAvailable() == null) {
        throw new IllegalStateException("endpoint permission authorizer is required");
      }
    };
  }

  @Bean
  ForgaEndpointPermissionInterceptor forgaEndpointPermissionInterceptor(
      ObjectProvider<EndpointPermissionAuthorizer> authorizers,
      EndpointPermissionResolverDelegate delegate) {
    return new ForgaEndpointPermissionInterceptor(
        delegate::resolve,
        invocation -> {
          EndpointPermissionAuthorizer authorizer = authorizers.getIfAvailable();
          if (authorizer == null) {
            throw new IllegalStateException("endpoint permission authorizer is required");
          }
          return authorizer.authorize(invocation);
        });
  }

  @Bean("forgaEndpointPermissionWebMvcConfigurer")
  WebMvcConfigurer forgaEndpointPermissionWebMvcConfigurer(
      ForgaEndpointPermissionInterceptor interceptor, ObjectProvider<ForgaWebScope> scopes) {
    ForgaWebScope scope = resolveScope(scopes);
    return new WebMvcConfigurer() {
      @Override
      public void addInterceptors(InterceptorRegistry registry) {
        registry
            .addInterceptor(interceptor)
            .addPathPatterns(scope.includePaths())
            .excludePathPatterns(scope.excludePaths());
      }
    };
  }

  private static ForgaWebScope resolveScope(ObjectProvider<ForgaWebScope> scopes) {
    List<ForgaWebScope> configuredScopes = scopes.orderedStream().toList();
    if (configuredScopes.size() != 1) {
      throw new IllegalStateException("exactly one Forga Web scope is required");
    }
    return configuredScopes.get(0);
  }

  private static void validateScope(
      RequestMappingInfo mapping, HandlerMethod handler, ForgaWebScope scope) {
    for (String path : mapping.getPatternValues()) {
      PathContainer candidate = PathContainer.parsePath(path);
      boolean included =
          scope.includePaths().stream()
              .anyMatch(p -> coversMapping(p, path, candidate));
      boolean excluded =
          scope.excludePaths().stream().anyMatch(p -> overlapsMapping(p, path, candidate));
      if (!included || excluded) {
        throw new IllegalStateException(
            "declared endpoint is outside Forga Web scope: " + handler + " at " + path);
      }
    }
  }

  private static boolean matches(String pattern, PathContainer path) {
    PathPattern parsed = PathPatternParser.defaultInstance.parse(pattern);
    return parsed.matches(path);
  }

  private static boolean coversMapping(String scopePattern, String mapping, PathContainer path) {
    if (!mapping.contains("{") && !mapping.contains("*")) {
      return matches(scopePattern, path);
    }
    if (scopePattern.equals(mapping) || scopePattern.equals("/**")) {
      return true;
    }
    if (scopePattern.endsWith("/**")) {
      String prefix = scopePattern.substring(0, scopePattern.length() - 3);
      String[] scopeSegments = prefix.split("/", -1);
      String[] mappingSegments = mapping.split("/", -1);
      if (scopeSegments.length > mappingSegments.length) {
        return false;
      }
      for (int index = 1; index < scopeSegments.length; index++) {
        String scopeSegment = scopeSegments[index];
        String mappingSegment = mappingSegments[index];
        if (!scopeSegment.equals(mappingSegment)
            && !(scopeSegment.startsWith("{")
                && scopeSegment.endsWith("}")
                && !scopeSegment.contains(":")
                && !scopeSegment.contains("*")
                && !mappingSegment.equals("**"))) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  private static boolean overlapsMapping(
      String scopePattern, String mapping, PathContainer mappingPath) {
    if (matches(scopePattern, mappingPath)) {
      return true;
    }
    String[] mappingSegments = mapping.split("/", -1);
    String[] scopeSegments = scopePattern.split("/", -1);
    StringBuilder probe = new StringBuilder();
    for (int index = 1; index < mappingSegments.length; index++) {
      String segment = mappingSegments[index];
      if (segment.equals("**")) {
        for (int rest = index; rest < scopeSegments.length; rest++) {
          String scopeSegment = scopeSegments[rest];
          probe.append('/').append(scopeSegment.contains("*") ? "forga-probe" : scopeSegment);
        }
        break;
      }
      if (segment.startsWith("{") || segment.contains("*")) {
        String scopeSegment = index < scopeSegments.length ? scopeSegments[index] : "";
        segment =
            scopeSegment.isEmpty()
                    || scopeSegment.startsWith("{")
                    || scopeSegment.contains("*")
                ? "forga-probe"
                : scopeSegment;
      }
      probe.append('/').append(segment);
    }
    PathContainer probePath = PathContainer.parsePath(probe.toString());
    return matches(mapping, probePath) && matches(scopePattern, probePath);
  }

  static final class EndpointPermissionResolverDelegate {

    private EndpointPermissionResolver delegate;

    void initialize(EndpointPermissionResolver resolver) {
      if (delegate != null) {
        throw new IllegalStateException("endpoint permission resolver is already initialized");
      }
      delegate = Objects.requireNonNull(resolver, "resolver is required");
    }

    Optional<EndpointPermissionRequirement> resolve(
        HandlerMethod handlerMethod, HttpServletRequest request) {
      if (delegate == null) {
        throw new IllegalStateException("endpoint permission resolver is not initialized");
      }
      return delegate.resolve(handlerMethod, request);
    }
  }
}
