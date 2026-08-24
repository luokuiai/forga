package com.luokuiai.forga.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.luokuiai.forga.core.eval.AuthorizationEvaluator;
import com.luokuiai.forga.core.eval.CaveatEvaluator;
import com.luokuiai.forga.core.eval.CheckDecision;
import com.luokuiai.forga.core.eval.CheckRequest;
import com.luokuiai.forga.core.eval.EvaluationLimits;
import com.luokuiai.forga.core.eval.ObjectListingLookup;
import com.luokuiai.forga.core.eval.RelationshipLookup;
import com.luokuiai.forga.core.model.AttributeRef;
import com.luokuiai.forga.core.model.CaveatRef;
import com.luokuiai.forga.core.model.ObjectRef;
import com.luokuiai.forga.core.model.PermissionRef;
import com.luokuiai.forga.core.model.RelationRef;
import com.luokuiai.forga.core.model.SubjectRef;
import com.luokuiai.forga.core.policy.CompiledPolicy;
import com.luokuiai.forga.core.policy.PermissionExpression;
import com.luokuiai.forga.core.policy.PolicyCompiler;
import com.luokuiai.forga.core.policy.PolicyDefinition;
import com.luokuiai.forga.core.policy.ResolverCapabilities;
import com.luokuiai.forga.resolver.AttributeResolutionBatchRequest;
import com.luokuiai.forga.resolver.AttributeResolutionBatchResponse;
import com.luokuiai.forga.resolver.DirectSubject;
import com.luokuiai.forga.resolver.ForwardRelationshipBatchRequest;
import com.luokuiai.forga.resolver.ForwardRelationshipBatchResponse;
import com.luokuiai.forga.resolver.ForwardRelationshipResponse;
import com.luokuiai.forga.resolver.RelationshipResolver;
import com.luokuiai.forga.resolver.ResolverDescriptor;
import com.luokuiai.forga.resolver.ResolverRegistry;
import com.luokuiai.forga.resolver.ReverseRelationshipBatchRequest;
import com.luokuiai.forga.resolver.ReverseRelationshipBatchResponse;
import com.luokuiai.forga.resolver.ReverseRelationshipResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ForgaEvaluatorAutoConfigurationTest {

  private static final RelationRef VIEWER = new RelationRef("viewer");

  private static final PermissionRef VIEW = new PermissionRef("view");

  private static final CaveatRef ACTIVE = new CaveatRef("active");

  private static final ObjectRef DOCUMENT = new ObjectRef("document", "one");

  private static final SubjectRef ALICE = new SubjectRef("principal", "alice");

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ForgaEvaluatorAutoConfiguration.class));

  @Test
  void evaluatorAutoConfigurationIsDiscoverable() {
    List<String> candidates =
        ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates();

    assertThat(candidates).contains(ForgaEvaluatorAutoConfiguration.class.getName());
  }

  @Test
  void enabledRuntimeAssemblesEvaluatorAndDefaultDependencies() {
    contextRunner
        .withUserConfiguration(EnabledConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(AuthorizationEvaluator.class);
              assertThat(context).hasSingleBean(ResolverRegistry.class);
              assertThat(context).hasSingleBean(RelationshipLookup.class);
              assertThat(context).hasSingleBean(ObjectListingLookup.class);
              assertThat(context).hasSingleBean(EvaluationLimits.class);

              CheckDecision decision =
                  context
                      .getBean(AuthorizationEvaluator.class)
                      .check(new CheckRequest(DOCUMENT, VIEW, ALICE));
              assertThat(decision.allowed()).isTrue();
            });
  }

  @Test
  void disabledRuntimeAssemblesNothing() {
    contextRunner
        .withUserConfiguration(DisabledConfiguration.class)
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(AuthorizationEvaluator.class);
              assertThat(context).doesNotHaveBean(ResolverRegistry.class);
              assertThat(context).doesNotHaveBean(EvaluationLimits.class);
            });
  }

  @Test
  void missingPolicyFailsStartup() {
    contextRunner
        .withUserConfiguration(MissingPolicyConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasMessageContaining(CompiledPolicy.class.getName());
            });
  }

  @Test
  void missingResolverCapabilityFailsStartup() {
    contextRunner
        .withUserConfiguration(MissingCapabilityConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage("missing forward resolver for relation: viewer");
            });
  }

  @Test
  void hostEvaluatorOverrideBacksOffWithoutPolicy() {
    contextRunner
        .withUserConfiguration(OverrideConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(AuthorizationEvaluator.class);
              assertThat(context.getBean(AuthorizationEvaluator.class))
                  .isSameAs(OverrideConfiguration.EVALUATOR);
            });
  }

  @Test
  void hostCaveatEvaluatorIsApplied() {
    contextRunner
        .withUserConfiguration(CaveatConfiguration.class)
        .run(
            context -> {
              CheckDecision decision =
                  context
                      .getBean(AuthorizationEvaluator.class)
                      .check(new CheckRequest(DOCUMENT, VIEW, ALICE));
              assertThat(decision.allowed()).isTrue();
            });
  }

  private static CompiledPolicy policy(PermissionExpression expression) {
    return policy(expression, List.of());
  }

  private static CompiledPolicy policy(
      PermissionExpression expression, List<CaveatRef> caveats) {
    return PolicyCompiler.compile(
        new PolicyDefinition(Map.of(VIEW, expression)),
        ResolverCapabilities.of(List.of(VIEWER), caveats));
  }

  @Configuration(proxyBeanMethods = false)
  @EnableForga
  static class EnabledConfiguration {

    @Bean
    CompiledPolicy compiledPolicy() {
      return policy(PermissionExpression.relation(VIEWER));
    }

    @Bean
    RelationshipResolver relationshipResolver() {
      return new TestResolver(Set.of(VIEWER), Set.of());
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class DisabledConfiguration {

    @Bean
    CompiledPolicy compiledPolicy() {
      return policy(PermissionExpression.relation(VIEWER));
    }

    @Bean
    RelationshipResolver relationshipResolver() {
      return new TestResolver(Set.of(VIEWER), Set.of(VIEWER));
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableForga
  static class MissingPolicyConfiguration {

    @Bean
    RelationshipResolver relationshipResolver() {
      return new TestResolver(Set.of(VIEWER), Set.of(VIEWER));
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableForga
  static class MissingCapabilityConfiguration {

    @Bean
    CompiledPolicy compiledPolicy() {
      return policy(PermissionExpression.relation(VIEWER));
    }

    @Bean
    RelationshipResolver relationshipResolver() {
      return new TestResolver(Set.of(), Set.of());
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableForga
  static class OverrideConfiguration {

    private static final AuthorizationEvaluator EVALUATOR =
        new AuthorizationEvaluator(
            policy(PermissionExpression.relation(VIEWER)),
            requests -> Map.of(),
            EvaluationLimits.defaults());

    @Bean
    AuthorizationEvaluator authorizationEvaluator() {
      return EVALUATOR;
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableForga
  static class CaveatConfiguration extends EnabledConfiguration {

    @Override
    @Bean
    CompiledPolicy compiledPolicy() {
      return policy(
          PermissionExpression.caveat(PermissionExpression.relation(VIEWER), ACTIVE),
          List.of(ACTIVE));
    }

    @Bean
    CaveatEvaluator caveatEvaluator() {
      return (caveat, request) -> ACTIVE.equals(caveat);
    }
  }

  private record TestResolver(Set<RelationRef> forward, Set<RelationRef> reverse)
      implements RelationshipResolver {

    @Override
    public ResolverDescriptor descriptor() {
      return new ResolverDescriptor("test", forward, reverse, Set.<AttributeRef>of());
    }

    @Override
    public ForwardRelationshipBatchResponse resolveForward(
        ForwardRelationshipBatchRequest request) {
      return new ForwardRelationshipBatchResponse(
          request.requests().stream()
              .map(item -> new ForwardRelationshipResponse(item, List.of(new DirectSubject(ALICE))))
              .toList());
    }

    @Override
    public ReverseRelationshipBatchResponse resolveReverse(
        ReverseRelationshipBatchRequest request) {
      return new ReverseRelationshipBatchResponse(
          request.requests().stream()
              .map(item -> new ReverseRelationshipResponse(item, List.of(DOCUMENT)))
              .toList());
    }

    @Override
    public AttributeResolutionBatchResponse resolveAttributes(
        AttributeResolutionBatchRequest request) {
      return null;
    }
  }
}
