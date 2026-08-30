package com.luokuiai.forga.resolver.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.luokuiai.forga.resolver.ForwardRelationshipBatchRequest;
import com.luokuiai.forga.resolver.ForwardRelationshipBatchResponse;
import com.luokuiai.forga.resolver.ForwardRelationshipResolver;
import org.junit.jupiter.api.Test;

/** Reusable contract tests for forward relationship resolvers. */
public abstract class ForwardRelationshipResolverContract {

  /**
   * Returns the resolver under test.
   *
   * @return resolver under test
   */
  protected abstract ForwardRelationshipResolver resolver();

  /**
   * Returns a batch expected to resolve at least one subject.
   *
   * @return forward batch request
   */
  protected abstract ForwardRelationshipBatchRequest forwardBatch();

  @Test
  final void forwardResponsesAreBoundedAndCarryConsistency() {
    ForwardRelationshipBatchResponse response = resolver().resolveForward(forwardBatch());

    assertThat(response.responses()).hasSameSizeAs(forwardBatch().requests());
    response.responses()
        .forEach(
            item -> {
              assertThat(item.subjects()).hasSizeLessThanOrEqualTo(item.request().limit());
              assertThat(item.consistency()).isNotNull();
            });
  }
}
