package com.luokuiai.forga.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ForgaWebScopeTest {

  @Test
  void requiresAnAbsoluteValidIncludePattern() {
    assertThatThrownBy(() -> new ForgaWebScope(List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("include path");
    assertThatThrownBy(() -> ForgaWebScope.include("api/**"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid Forga Web path pattern");
    assertThatThrownBy(() -> ForgaWebScope.include("/api/{"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void copiesHostLists() {
    ForgaWebScope scope = new ForgaWebScope(List.of("/api/**"), List.of("/api/public/**"));

    assertThat(scope.includePaths()).containsExactly("/api/**");
    assertThat(scope.excludePaths()).containsExactly("/api/public/**");
    assertThatThrownBy(() -> scope.includePaths().add("/other/**"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
