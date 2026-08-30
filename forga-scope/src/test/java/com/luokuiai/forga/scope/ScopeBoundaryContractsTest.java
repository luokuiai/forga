package com.luokuiai.forga.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.luokuiai.forga.core.model.AttributeRef;
import com.luokuiai.forga.core.model.ObjectRef;
import com.luokuiai.forga.core.model.PermissionRef;
import com.luokuiai.forga.core.model.SubjectRef;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScopeBoundaryContractsTest {

  private static final ScopeRef ACTIVE_SCOPE = new ScopeRef("workspace", "alpha");

  private static final ScopeRef OBJECT_SCOPE = new ScopeRef("workspace", "beta");

  private static final ObjectRef OBJECT = new ObjectRef("report", "report-1");

  private static final PermissionRef PERMISSION = new PermissionRef("view");

  private static final SubjectRef SUBJECT = new SubjectRef("principal", "alice");

  @Test
  void createsImmutableCrossScopeAccessRequest() {
    AttributeRef region = new AttributeRef("region");
    Map<AttributeRef, String> attributes = new HashMap<>();
    attributes.put(region, "east");
    CrossScopeAccessRequest request =
        new CrossScopeAccessRequest(
            ACTIVE_SCOPE, OBJECT_SCOPE, OBJECT, PERMISSION, SUBJECT, attributes);

    attributes.clear();

    assertThat(request.activeScope()).isEqualTo(ACTIVE_SCOPE);
    assertThat(request.objectScope()).isEqualTo(OBJECT_SCOPE);
    assertThat(request.attributes()).containsEntry(region, "east");
    assertThat(CrossScopeGrantLookup.denyAll().resolve(java.util.List.of(request)))
        .containsEntry(request, false);
  }

  @Test
  void rejectsInvalidCrossScopeAccessRequest() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new CrossScopeAccessRequest(
                    null, OBJECT_SCOPE, OBJECT, PERMISSION, SUBJECT, Map.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new CrossScopeAccessRequest(
                    ACTIVE_SCOPE, null, OBJECT, PERMISSION, SUBJECT, Map.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new CrossScopeAccessRequest(
                    ACTIVE_SCOPE, OBJECT_SCOPE, null, PERMISSION, SUBJECT, Map.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new CrossScopeAccessRequest(
                    ACTIVE_SCOPE, OBJECT_SCOPE, OBJECT, null, SUBJECT, Map.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new CrossScopeAccessRequest(
                    ACTIVE_SCOPE, OBJECT_SCOPE, OBJECT, PERMISSION, null, Map.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new CrossScopeAccessRequest(
                    ACTIVE_SCOPE, OBJECT_SCOPE, OBJECT, PERMISSION, SUBJECT, null));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new CrossScopeAccessRequest(
                    ACTIVE_SCOPE, ACTIVE_SCOPE, OBJECT, PERMISSION, SUBJECT, Map.of()));
  }
}
