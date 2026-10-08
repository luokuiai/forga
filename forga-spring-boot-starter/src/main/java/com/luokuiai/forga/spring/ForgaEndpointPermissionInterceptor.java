package com.luokuiai.forga.spring;

import com.luokuiai.forga.spring.web.EndpointAuthorizationDecision;
import com.luokuiai.forga.spring.web.EndpointAuthorizationException;
import com.luokuiai.forga.spring.web.EndpointInvocation;
import com.luokuiai.forga.spring.web.EndpointPermissionAuthorizer;
import com.luokuiai.forga.spring.web.EndpointPermissionRequirement;
import com.luokuiai.forga.spring.web.EndpointPermissionResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Starter-owned MVC enforcement hook; hosts configure scope, not this interceptor. */
final class ForgaEndpointPermissionInterceptor implements HandlerInterceptor {

  private final EndpointPermissionResolver resolver;

  private final EndpointPermissionAuthorizer authorizer;

  ForgaEndpointPermissionInterceptor(
      EndpointPermissionResolver resolver, EndpointPermissionAuthorizer authorizer) {
    this.resolver = Objects.requireNonNull(resolver, "resolver is required");
    this.authorizer = Objects.requireNonNull(authorizer, "authorizer is required");
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return true;
    }
    EndpointPermissionRequirement requirement =
        resolver
            .resolve(handlerMethod, request)
            .orElseThrow(EndpointAuthorizationException::unresolved);
    if (requirement.isPermitAll()) {
      return true;
    }
    EndpointInvocation invocation =
        new EndpointInvocation(
            requirement.permission().orElseThrow(),
            handlerMethod.getMethod(),
            handlerMethod.getBean(),
            request);
    EndpointAuthorizationDecision decision = authorizer.authorize(invocation);
    if (decision == null) {
      decision =
          EndpointAuthorizationDecision.denied(
              invocation.permission(), "AUTHORIZER_RETURNED_NULL");
    }
    if (!decision.allowed()) {
      throw EndpointAuthorizationException.denied(decision);
    }
    return true;
  }
}
