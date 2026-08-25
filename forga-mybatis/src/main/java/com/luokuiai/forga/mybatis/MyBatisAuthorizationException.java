package com.luokuiai.forga.mybatis;

/**
 * Raised when enabled MyBatis authorization fails closed.
 */
public final class MyBatisAuthorizationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates an authorization exception.
   *
   * @param message failure message
   */
  public MyBatisAuthorizationException(String message) {
    super(message);
  }

  /**
   * Creates an authorization exception with its cause.
   *
   * @param message failure message
   * @param cause underlying failure
   */
  public MyBatisAuthorizationException(String message, Throwable cause) {
    super(message, cause);
  }
}
