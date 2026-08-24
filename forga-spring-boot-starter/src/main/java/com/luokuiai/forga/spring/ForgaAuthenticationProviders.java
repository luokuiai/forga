package com.luokuiai.forga.spring;

import com.luokuiai.forga.core.context.AuthenticatedSubjectProvider;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;

final class ForgaAuthenticationProviders {

  private ForgaAuthenticationProviders() {
  }

  static AuthenticatedSubjectProvider requireOne(
      ObjectProvider<AuthenticatedSubjectProvider> providers) {
    List<AuthenticatedSubjectProvider> candidates = providers.orderedStream().toList();
    if (candidates.isEmpty()) {
      throw new ForgaRuntimeException("enabled integration requires an authentication provider");
    }
    if (candidates.size() > 1) {
      throw new ForgaRuntimeException(
          "enabled integration requires exactly one authentication provider, found "
              + candidates.size());
    }
    return candidates.get(0);
  }
}
