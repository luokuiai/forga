package com.luokuiai.forga.mybatis;

/**
 * Raised when a typed constraint cannot be translated safely.
 */
public final class MyBatisTranslationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates a translation exception.
   *
   * @param message failure message
   */
  public MyBatisTranslationException(String message) {
    super(message);
  }

  MyBatisTranslationException(String message, Throwable cause) {
    super(message, cause);
  }
}
