package com.luokuiai.forga.resolver;

/** Named host resolver registered with the Forga runtime. */
public interface Resolver {

  /**
   * Returns the stable resolver name used in diagnostics and ownership validation.
   *
   * @return stable resolver name
   */
  String name();
}
