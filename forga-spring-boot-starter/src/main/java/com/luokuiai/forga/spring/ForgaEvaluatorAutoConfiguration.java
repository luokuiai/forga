package com.luokuiai.forga.spring;

import com.luokuiai.forga.core.eval.AuthorizationEvaluator;
import com.luokuiai.forga.core.eval.CaveatEvaluator;
import com.luokuiai.forga.core.eval.EvaluationLimits;
import com.luokuiai.forga.core.eval.ObjectListingLookup;
import com.luokuiai.forga.core.eval.RelationshipLookup;
import com.luokuiai.forga.core.policy.CompiledPolicy;
import com.luokuiai.forga.resolver.RelationshipResolver;
import com.luokuiai.forga.resolver.ResolverRegistry;
import com.luokuiai.forga.resolver.ResolverRegistryObjectListingLookup;
import com.luokuiai.forga.resolver.ResolverRegistryRelationshipLookup;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Spring Boot assembly for the core Forga authorization evaluator. */
@AutoConfiguration
@ConditionalOnForgaEnabled
public class ForgaEvaluatorAutoConfiguration {

  /**
   * Registers host relationship resolvers.
   *
   * @param resolvers host relationship resolvers
   * @return resolver registry
   */
  @Bean
  @ConditionalOnMissingBean
  public ResolverRegistry forgaResolverRegistry(List<RelationshipResolver> resolvers) {
    return new ResolverRegistry(resolvers);
  }

  /**
   * Adapts registered forward resolvers to evaluator relationship lookups.
   *
   * @param resolvers resolver registry
   * @return relationship lookup
   */
  @Bean
  @ConditionalOnMissingBean(RelationshipLookup.class)
  public RelationshipLookup forgaRelationshipLookup(ResolverRegistry resolvers) {
    return new ResolverRegistryRelationshipLookup(resolvers);
  }

  /**
   * Adapts registered reverse resolvers to evaluator object listings.
   *
   * @param resolvers resolver registry
   * @return object listing lookup
   */
  @Bean
  @ConditionalOnMissingBean(ObjectListingLookup.class)
  public ObjectListingLookup forgaObjectListingLookup(ResolverRegistry resolvers) {
    return new ResolverRegistryObjectListingLookup(resolvers);
  }

  /**
   * Provides conservative evaluator bounds.
   *
   * @return default evaluation limits
   */
  @Bean
  @ConditionalOnMissingBean
  public EvaluationLimits forgaEvaluationLimits() {
    return EvaluationLimits.defaults();
  }

  /**
   * Assembles the core authorization evaluator.
   *
   * @param policy host compiled policy
   * @param resolvers resolver registry
   * @param relationships forward relationship lookup
   * @param objectListings reverse object listing lookup
   * @param limits evaluation limits
   * @param caveats optional caveat evaluator
   * @return authorization evaluator
   */
  @Bean
  @ConditionalOnMissingBean
  public AuthorizationEvaluator forgaAuthorizationEvaluator(
      CompiledPolicy policy,
      ResolverRegistry resolvers,
      RelationshipLookup relationships,
      ObjectListingLookup objectListings,
      EvaluationLimits limits,
      ObjectProvider<CaveatEvaluator> caveats) {
    ForgaResolverValidator.validateForwardCapabilities(policy, resolvers);
    CaveatEvaluator caveatEvaluator = caveats.getIfAvailable();
    return caveatEvaluator == null
        ? new AuthorizationEvaluator(policy, relationships, objectListings, limits)
        : new AuthorizationEvaluator(
            policy, relationships, objectListings, limits, caveatEvaluator);
  }
}
