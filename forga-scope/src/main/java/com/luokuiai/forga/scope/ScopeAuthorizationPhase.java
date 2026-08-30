package com.luokuiai.forga.scope;

/** Stage that produced a scoped authorization decision. */
public enum ScopeAuthorizationPhase {
  /** The request did not contain a usable active scope. */
  ACTIVE_SCOPE,

  /** Authorization to enter the selected scope was evaluated. */
  SCOPE_ENTRY,

  /** The protected object's owning scope was resolved. */
  OBJECT_SCOPE,

  /** An explicit grant across different scopes was evaluated. */
  CROSS_SCOPE_GRANT,

  /** The ordinary permission on the protected object was evaluated. */
  OBJECT_PERMISSION
}
