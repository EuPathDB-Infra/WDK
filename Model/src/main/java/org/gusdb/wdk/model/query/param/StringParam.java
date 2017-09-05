package org.gusdb.wdk.model.query.param;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkModelText;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.user.User;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The StringParam is used to accept user inputs from a input box.
 * 
 * The author can provide regex to limit the content of user's input. The input will be rejected if regex
 * match fails. Furthermore, you can also define a default regex in the model-config.xml, which will be used
 * by all StringParams who don't have their own regex defined.
 * 
 * If the number flag is set to true, only numbers are allowed (integers, floats, or doubles). You can also
 * provide regex to limit the allowed value for numbers.
 * 
 * @author xingao
 * 
 *         raw value: a raw string;
 * 
 *         stable value: same as raw value;
 * 
 *         signature: a checksum of the stable value;
 * 
 *         internal value: if number is true, the internal is a string representation of a parsed Double;
 *         otherwise, quotes are properly applied; If noTranslation is true, the raw value is used without any
 *         change.
 */
public class StringParam extends Param {

  private List<WdkModelText> _regexes;
  private String _regex;
  private int _length = 0;

  private boolean _isNumber = false;
  private boolean _isSql = false;
  private boolean _multiLine = false;

  public StringParam() {
    _regexes = new ArrayList<WdkModelText>();

    // register handler
    setHandler(new StringParamHandler());
  }

  public StringParam(StringParam param) {
    super(param);
    if (param._regexes != null)
      _regexes = new ArrayList<WdkModelText>();
    _regex = param._regex;
    _length = param._length;
    _isNumber = param._isNumber;
    _isSql = param._isSql;
    _multiLine = param._multiLine;
  }

  // ///////////////////////////////////////////////////////////////////
  // /////////// Public properties ////////////////////////////////////
  // ///////////////////////////////////////////////////////////////////

  public void addRegex(WdkModelText regex) {
    _regexes.add(regex);
  }

  public void setRegex(String regex) {
    _regex = regex;
  }

  public String getRegex() {
    return _regex;
  }

  /**
   * @return the length
   */
  public int getLength() {
    return _length;
  }

  /**
   * @param length
   *          the length to set
   */
  public void setLength(int length) {
    _length = length;
  }

  /**
   * @return the isNumber
   */
  public boolean isNumber() {
    return _isNumber;
  }

  /**
   * @param isNumber
   *          the isNumber to set
   */
  public void setNumber(boolean isNumber) {
    _isNumber = isNumber;
  }

  /**
   * @return the isSql
   */
  public boolean getIsSql() {
    return _isSql;
  }

  /**
   * @param isNumber
   *          the isNumber to set
   */
  public void setIsSql(boolean isSql) {
    _isSql = isSql;
  }

  /**
   * Whether this param will render as a textarea instead of a textbox
   * 
   * @param multiLine
   *          set to true if textarea is desired (default false)
   */
  public void setMultiLine(boolean multiLine) {
    _multiLine = multiLine;
  }

  /**
   * This property controls the display of the input box. If multiLine is false, a normal single-line input
   * box will be used, and if true, a multi-line text area will be used.
   * 
   * @return
   */
  public boolean getMultiLine() {
    return _multiLine;
  }

  @Override
  public String toString() {
    String newline = System.getProperty("line.separator");
    return new StringBuilder(super.toString())
    // .append("  sample='").append(sample).append("'").append(newline)
    .append("  regex='").append(_regex).append("'").append(newline).append("  length='").append(_length).append(
        "'").append(newline).append("  multiLine='").append(_multiLine).append("'").toString();
  }

  // ///////////////////////////////////////////////////////////////
  // protected methods
  // ///////////////////////////////////////////////////////////////

  @Override
  public void resolveReferences(WdkModel model) throws WdkModelException {
    super.resolveReferences(model);
    if (_regex == null)
      _regex = model.getModelConfig().getParamRegex();
    if (_regex == null && isNumber()) {
      _regex = "[+-]?\\d+(\\.\\d+)?([eE][+-]?\\d+)?";
    }
  }

  @Override
  public Param clone() {
    return new StringParam(this);
  }

  @Override
  protected void appendChecksumJSON(JSONObject jsParam, boolean extra) throws JSONException {
    // nothing to be added
  }

  @Override
  protected void validateValue(User user, String stableValue, Map<String, String> contextParamValues)
      throws WdkUserException, WdkModelException {
    if (_isNumber) {
      try {
        // strip off the comma, if any
        String value = stableValue.replaceAll(",", "");
        Double.valueOf(value);
      }
      catch (NumberFormatException ex) {
        throw new WdkUserException("value must be numerical; '" + stableValue + "' is invalid.");
      }
    }
    if (_regex != null && !stableValue.matches(_regex)) {
      if (stableValue.equals("*"))
        throw new WdkUserException("value '" + stableValue +
            "' cannot be used on its own; it needs to be part of a word.");
      else
        throw new WdkUserException("value '" + stableValue + "' is " +
            "invalid and probably contains illegal characters. " + "It must match the regular expression '" +
            _regex + "'");
    }
    if (_length != 0 && stableValue.length() > _length)
      throw new WdkUserException("value cannot be longer than " + _length + " characters (it is " +
          stableValue.length() + ").");
  }

  @Override
  public void excludeResources(String projectId) throws WdkModelException {
    super.excludeResources(projectId);

    boolean hasRegex = false;
    for (WdkModelText regex : _regexes) {
      if (regex.include(projectId)) {
        if (hasRegex) {
          throw new WdkModelException("The param " + getFullName() + " has more than one regex for project " +
              projectId);
        }
        else {
          _regex = regex.getText();
          hasRegex = true;
        }
      }
    }
    _regexes = null;
  }

  @Override
  protected void applySuggestion(ParamSuggestion suggest) {
    // do nothing
  }

  @Override
  public String getBriefRawValue(Object rawValue, int truncateLength) throws WdkModelException {
    String value = (String) rawValue;
    if (value == null) return value;
    if (value.length() > truncateLength)
      value = value.substring(0, truncateLength) + "...";
    return value;
  }

}
