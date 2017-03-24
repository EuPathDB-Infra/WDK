package org.gusdb.wdk.model;

import java.util.Map;

/**
 * This exception should be thrown out if the cause is not related to user's
 * input. For example, the cause of the exception can be a mistake in the model
 * file, the database resource is unavailable, etc.
 * 
 * @author jerric
 * 
 */
public class WdkModelException extends WdkException implements ParameterErrors {

  private static final long serialVersionUID = 877548355767390313L;

  private Map<String, String> paramErrors = null;

  public WdkModelException(Map<String, String> paramErrors) {
    super();
    this.paramErrors = paramErrors;
  }

  public WdkModelException() {
    super();
  }

  public WdkModelException(String msg) {
    super(msg);
  }

  public WdkModelException(String msg, Throwable cause) {
    super(msg, cause);
  }

  public WdkModelException(Throwable cause) {
    super(cause);
    if (cause instanceof ParameterErrors) {
      this.paramErrors = ((ParameterErrors)cause).getParamErrors();
    }
  }

  public WdkModelException(String message, Map<String, String> paramErrors) {
    super(message);
    this.paramErrors = paramErrors;
  }

  public WdkModelException(Throwable cause, Map<String, String> paramErrors) {
    super(cause);
    this.paramErrors = paramErrors;
  }

  public WdkModelException(String message, Throwable cause,
      Map<String, String> paramErrors) {
    super(message, cause);
    this.paramErrors = paramErrors;
  }

  /**
   * @return Map where keys are Params and values are an tuple of (value,
   *         errMsg), one for each error param value
   */
  @Override
  public Map<String, String> getParamErrors() {
    return paramErrors;
  }

  /**
   * @return A default formatting of contained errors
   */
  @Override
  public String formatErrors() {

    String newline = System.getProperty("line.separator");
    StringBuffer buf = new StringBuffer(super.formatErrors());
    buf.append(newline);
    if (paramErrors != null) {
      for (String paramPrompt : paramErrors.keySet()) {
        String details = paramErrors.get(paramPrompt);
        buf.append(paramPrompt + ": " + details + newline);
      }
    }
    return buf.toString();
  }
}
