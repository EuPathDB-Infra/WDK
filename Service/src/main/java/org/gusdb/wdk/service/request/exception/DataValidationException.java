package org.gusdb.wdk.service.request.exception;

import java.util.Map;

import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.FormatUtil.Style;
import org.gusdb.wdk.model.WdkUserException;

/**
 * Custom service exception to be thrown when the JSON received is syntactically 
 * correct but problems with the content preclude the successful completion of
 * the web service.
 * 
 * @author crisl-adm
 */
public class DataValidationException extends Exception {

  private static final long serialVersionUID = 1L;
  private static final String DEFAULT_MESSAGE = "HTTP 422 Unprocessable Entity";

  /** 
   * No arg constructor 
   * Passing default message to superclass
   */
  public DataValidationException() {
    super(DEFAULT_MESSAGE);
  }

  /**
   * Passing the message into the superclass
   * @param message
   */
  public DataValidationException(String message) {
      super(message);
  }
  
  /**
   * Passing the throwable into the superclass
   * and applying the default message 
   * @param t
   */
  public DataValidationException(Throwable t) {
    super(determineMessage(t), t);
  }

  private static String determineMessage(Throwable t) {
    if (t instanceof WdkUserException) {
      WdkUserException e = (WdkUserException)t;
      Map<String,String> paramErrors = e.getParamErrors();
      return (paramErrors == null ? e.getMessage() : FormatUtil.prettyPrint(paramErrors, Style.MULTI_LINE));
    }
    return DEFAULT_MESSAGE;
  }

  /**
   * Passing both a message and the throwable into the superclass
   * @param message
   * @param t
   */
  public DataValidationException(String message, Throwable t) {
    super(message, t);
  }

}
