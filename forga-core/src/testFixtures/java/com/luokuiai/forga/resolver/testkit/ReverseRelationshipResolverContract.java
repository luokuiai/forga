package com.luokuiai.forga.resolver.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.luokuiai.forga.resolver.ReverseRelationshipBatchRequest;
import com.luokuiai.forga.resolver.ReverseRelationshipBatchResponse;
import com.luokuiai.forga.resolver.ReverseRelationshipResolver;
import org.junit.jupiter.api.Test;

/** Reusable contract tests for reverse relationship resolvers. */
public abstract class ReverseRelationshipResolverContract {

  /**
   * Returns the resolver under test.
   *
   * @return resolver under test
   */
  protected abstract ReverseRelationshipResolver resolver();

  /**
   * Returns a batch expected to resolve at least one object.
   *
   * @return reverse batch request
   */
  protected abstract ReverseRelationshipBatchRequest reverseBatch();

  @Test
  final void reverseResponsesAreBoundedAndCarryStableCursorState() {
    ReverseRelationshipBatchResponse response = resolver().resolveReverse(reverseBatch());

    assertThat(response.responses()).hasSameSizeAs(reverseBatch().requests());
    response.responses()
        .forEach(
            item -> {
              assertThat(item.objects()).hasSizeLessThanOrEqualTo(item.request().limit());
              item.nextCursor().ifPresent(cursor -> assertThat(cursor.value()).isNotBlank());
              assertThat(item.consistency()).isNotNull();
            });
  }
}
