package com.luokuiai.forga.resolver.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.luokuiai.forga.resolver.AttributeResolutionBatchRequest;
import com.luokuiai.forga.resolver.AttributeResolutionBatchResponse;
import com.luokuiai.forga.resolver.AttributeResolver;
import org.junit.jupiter.api.Test;

/** Reusable contract tests for object attribute resolvers. */
public abstract class AttributeResolverContract {

  /**
   * Returns the resolver under test.
   *
   * @return resolver under test
   */
  protected abstract AttributeResolver resolver();

  /**
   * Returns a batch expected to resolve every requested attribute.
   *
   * @return attribute batch request
   */
  protected abstract AttributeResolutionBatchRequest attributeBatch();

  @Test
  final void attributeResponsesMatchRequestedAttributes() {
    AttributeResolutionBatchResponse response = resolver().resolveAttributes(attributeBatch());

    assertThat(response.responses()).hasSameSizeAs(attributeBatch().requests());
    response.responses()
        .forEach(
            item -> {
              assertThat(item.attributes())
                  .extracting(attribute -> attribute.attribute())
                  .containsExactlyInAnyOrderElementsOf(item.request().attributes());
              assertThat(item.consistency()).isNotNull();
            });
  }
}
