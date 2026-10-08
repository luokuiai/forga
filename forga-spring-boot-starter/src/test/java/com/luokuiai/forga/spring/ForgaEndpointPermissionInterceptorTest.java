package com.luokuiai.forga.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luokuiai.forga.core.model.PermissionRef;
import com.luokuiai.forga.spring.web.DefaultEndpointPermissionResolver;
import com.luokuiai.forga.spring.web.EndpointAuthorizationDecision;
import com.luokuiai.forga.spring.web.EndpointAuthorizationException;
import com.luokuiai.forga.spring.web.EndpointInvocation;
import com.luokuiai.forga.spring.web.EndpointPermissionAuthorizer;
import com.luokuiai.forga.spring.web.EndpointPermissionResolver;
import com.luokuiai.forga.spring.web.RequiresPermission;
import jakarta.annotation.security.PermitAll;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class ForgaEndpointPermissionInterceptorTest {

  @Test
  void authorizesResolvedPermissionBeforeHandler() throws Exception {
    List<EndpointInvocation> invocations = new ArrayList<>();
    ForgaEndpointPermissionInterceptor interceptor =
        interceptor(
            new DefaultEndpointPermissionResolver(),
            invocation -> {
              invocations.add(invocation);
              return EndpointAuthorizationDecision.allowed(invocation.permission());
            });

    boolean handled = interceptor.preHandle(request(), response(), handler("meeting"));

    assertThat(handled).isTrue();
    assertThat(invocations).hasSize(1);
    assertThat(invocations.get(0).permission())
        .isEqualTo(new PermissionRef("meeting:view"));
  }

  @Test
  void skipsAuthorizerForExplicitPermitAll() throws Exception {
    List<EndpointInvocation> invocations = new ArrayList<>();
    ForgaEndpointPermissionInterceptor interceptor =
        interceptor(
            new DefaultEndpointPermissionResolver(),
            invocation -> {
              invocations.add(invocation);
              return EndpointAuthorizationDecision.allowed(invocation.permission());
            });

    assertThat(interceptor.preHandle(request(), response(), handler("health"))).isTrue();
    assertThat(invocations).isEmpty();
  }

  @Test
  void failsClosedForUnresolvedEndpoint() throws Exception {
    ForgaEndpointPermissionInterceptor interceptor =
        interceptor(
            (handler, request) -> Optional.empty(),
            invocation -> EndpointAuthorizationDecision.allowed(invocation.permission()));

    assertThatThrownBy(
            () -> interceptor.preHandle(request(), response(), handler("plain")))
        .isInstanceOf(EndpointAuthorizationException.class)
        .extracting(exception -> ((EndpointAuthorizationException) exception).reason())
        .isEqualTo("ENDPOINT_PERMISSION_UNRESOLVED");
  }

  @Test
  void failsClosedForDeniedAndNullAuthorizerDecisions() throws Exception {
    EndpointPermissionResolver resolver = new DefaultEndpointPermissionResolver();
    ForgaEndpointPermissionInterceptor denied =
        interceptor(
            resolver,
            invocation ->
                EndpointAuthorizationDecision.denied(
                    invocation.permission(), "PERMISSION_NOT_GRANTED"));
    ForgaEndpointPermissionInterceptor nullDecision = interceptor(resolver, invocation -> null);

    assertThatThrownBy(() -> denied.preHandle(request(), response(), handler("meeting")))
        .isInstanceOf(EndpointAuthorizationException.class)
        .extracting(exception -> ((EndpointAuthorizationException) exception).reason())
        .isEqualTo("PERMISSION_NOT_GRANTED");
    assertThatThrownBy(() -> nullDecision.preHandle(request(), response(), handler("meeting")))
        .isInstanceOf(EndpointAuthorizationException.class)
        .extracting(exception -> ((EndpointAuthorizationException) exception).reason())
        .isEqualTo("AUTHORIZER_RETURNED_NULL");
  }

  @Test
  void ignoresNonHandlerObjects() {
    ForgaEndpointPermissionInterceptor interceptor =
        interceptor(
            (handler, request) -> Optional.empty(),
            invocation -> EndpointAuthorizationDecision.allowed(invocation.permission()));

    assertThat(interceptor.preHandle(request(), response(), new Object())).isTrue();
  }

  private static ForgaEndpointPermissionInterceptor interceptor(
      EndpointPermissionResolver resolver, EndpointPermissionAuthorizer authorizer) {
    return new ForgaEndpointPermissionInterceptor(resolver, authorizer);
  }

  private static HandlerMethod handler(String method) throws Exception {
    return new HandlerMethod(new DemoController(), method);
  }

  private static MockHttpServletRequest request() {
    return new MockHttpServletRequest();
  }

  private static MockHttpServletResponse response() {
    return new MockHttpServletResponse();
  }

  private static final class DemoController {

    @RequiresPermission("meeting:view")
    public void meeting() {
    }

    @PermitAll
    public void health() {
    }

    public void plain() {
    }
  }
}
