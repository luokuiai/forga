package com.luokuiai.forga.core.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.luokuiai.forga.core.model.CaveatRef;
import com.luokuiai.forga.core.model.PermissionRef;
import com.luokuiai.forga.core.model.RelationRef;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PolicyCompilerTest {

  @Test
  void compilesPolicyWithStableFingerprint() {
    PolicyDefinition first =
        new PolicyDefinition(
            Map.of(
                new PermissionRef("view"),
                PermissionExpression.caveat(
                    PermissionExpression.union(
                        List.of(
                            PermissionExpression.relation(new RelationRef("viewer")),
                            PermissionExpression.traversal(
                                new RelationRef("parent"),
                                PermissionExpression.relation(new RelationRef("owner"))))),
                    new CaveatRef("active"))));
    PolicyDefinition reordered =
        new PolicyDefinition(new LinkedHashMap<>(first.permissions()));

    CompiledPolicy compiled = PolicyCompiler.compile(first);
    CompiledPolicy compiledAgain = PolicyCompiler.compile(reordered);

    assertThat(compiled.definition()).isEqualTo(first);
    assertThat(compiled.fingerprint()).startsWith("sha256:");
    assertThat(compiled.fingerprint()).hasSize(71);
    assertThat(compiledAgain.fingerprint()).isEqualTo(compiled.fingerprint());
  }

  @Test
  void fingerprintDoesNotDependOnPermissionMapOrder() {
    PermissionExpression viewer = PermissionExpression.relation(new RelationRef("viewer"));
    PermissionExpression editor = PermissionExpression.relation(new RelationRef("editor"));
    Map<PermissionRef, PermissionExpression> first = new LinkedHashMap<>();
    first.put(new PermissionRef("view"), viewer);
    first.put(new PermissionRef("edit"), editor);
    Map<PermissionRef, PermissionExpression> second = new LinkedHashMap<>();
    second.put(new PermissionRef("edit"), editor);
    second.put(new PermissionRef("view"), viewer);

    assertThat(PolicyCompiler.compile(new PolicyDefinition(first)).fingerprint())
        .isEqualTo(
            PolicyCompiler.compile(new PolicyDefinition(second)).fingerprint());
  }

  @Test
  void compilesWithoutRuntimeRelationRegistrations() {
    PolicyDefinition definition =
        new PolicyDefinition(
            Map.of(
                new PermissionRef("view"),
                PermissionExpression.relation(new RelationRef("viewer"))));

    assertThat(PolicyCompiler.compile(definition).definition()).isEqualTo(definition);
  }

  @Test
  void compilesWithoutRuntimeCaveatRegistrations() {
    PolicyDefinition definition =
        new PolicyDefinition(
            Map.of(
                new PermissionRef("view"),
                PermissionExpression.caveat(
                    PermissionExpression.relation(new RelationRef("viewer")),
                    new CaveatRef("active"))));

    assertThat(PolicyCompiler.compile(definition).definition()).isEqualTo(definition);
  }

  @Test
  void discoversRuntimeRequirementsFromCompiledPolicy() {
    RelationRef viewer = new RelationRef("viewer");
    RelationRef parent = new RelationRef("parent");
    CaveatRef active = new CaveatRef("active");
    CompiledPolicy policy =
        PolicyCompiler.compile(
            new PolicyDefinition(
                Map.of(
                    new PermissionRef("view"),
                    PermissionExpression.union(
                        List.of(
                            PermissionExpression.grant(),
                            PermissionExpression.traversal(
                                parent,
                                PermissionExpression.caveat(
                                    PermissionExpression.relation(viewer), active)))))));

    PolicyRequirements requirements = PolicyRequirements.from(policy);

    assertThat(requirements.relations()).containsExactlyInAnyOrder(parent, viewer);
    assertThat(requirements.caveats()).containsExactly(active);
    assertThat(requirements.grantLookupRequired()).isTrue();
  }

  @Test
  void rejectsEmptyPolicyDefinitions() {
    assertThatIllegalArgumentException().isThrownBy(() -> new PolicyDefinition(Map.of()));
  }

  @Test
  void rejectsNullCompileInputs() {
    assertThatNullPointerException().isThrownBy(() -> PolicyCompiler.compile(null));
  }
}
