package org.gusdb.wdk.controller.action.user;

import java.util.Map;

import javax.servlet.http.Cookie;

import org.apache.log4j.Logger;
import org.gusdb.wdk.controller.CConstants;
import org.gusdb.wdk.controller.LoginCookieFactory;
import org.gusdb.wdk.controller.OAuthClient;
import org.gusdb.wdk.controller.actionutil.ActionResult;
import org.gusdb.wdk.controller.actionutil.ParamDef;
import org.gusdb.wdk.controller.actionutil.ParamDef.Count;
import org.gusdb.wdk.controller.actionutil.ParamDef.DataType;
import org.gusdb.wdk.controller.actionutil.ParamDef.Required;
import org.gusdb.wdk.controller.actionutil.ParamDefMapBuilder;
import org.gusdb.wdk.controller.actionutil.ParamGroup;
import org.gusdb.wdk.controller.actionutil.RequestData;
import org.gusdb.wdk.controller.actionutil.WdkAction;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.config.ModelConfig;
import org.gusdb.wdk.model.config.ModelConfig.AuthenticationMethod;
import org.gusdb.wdk.model.jspwrap.UserBean;
import org.gusdb.wdk.model.jspwrap.UserFactoryBean;
import org.gusdb.wdk.model.jspwrap.WdkModelBean;
import org.gusdb.wdk.session.OAuthUtil;

/**
 * @author: Jerric
 * @created: May 26, 2006
 * @modified by: Ryan
 * @modified at: June 30, 2012
 */
public class ProcessLoginAction extends WdkAction {

  private static final Logger LOG = Logger.getLogger(ProcessLoginAction.class.getName());
  
  private static final String REMEMBER_PARAM_KEY = "remember";
  
  private static final Map<String, ParamDef> PARAM_DEFS = new ParamDefMapBuilder()
      .addParam(CConstants.WDK_REDIRECT_URL_KEY,
          new ParamDef(Required.OPTIONAL, Count.SINGULAR, DataType.STRING, new String[]{ "/" }))
      .addParam(CConstants.WDK_EMAIL_KEY, new ParamDef(Required.OPTIONAL))
      .addParam(CConstants.WDK_PASSWORD_KEY, new ParamDef(Required.OPTIONAL))
      .addParam(REMEMBER_PARAM_KEY, new ParamDef(Required.OPTIONAL)).toMap();
  
  @Override
  protected boolean shouldValidateParams() {
    // only validate params if using traditional form
    return (getWdkModel().getModel().getModelConfig()
        .getAuthenticationMethodEnum().equals(AuthenticationMethod.USER_DB));
  }

  @Override
  protected Map<String, ParamDef> getParamDefs() {
    return PARAM_DEFS;
  }

  /**
   * Original logic for resulting page:
   *   check for referrer on request and on header (but not session)
   *   check for origin url on request
   *   redirect = true
   *   if (user is logged in)
   *     if (origin url exists)
   *       use origin url
   *       wipe origin url from session (how did it get there?)
   *     else
   *       use referrer
   *   else (i.e. user needs to log in)
   *     if (successful login)
   *       if (origin url exists)
   *         use origin url
   *         wipe origin url from session (how did it get there?)
   *       else
   *         use referrer
   *     else
   *       set referrer on session
   *       set origin url on session
   *       redirect = false
   *       look for custom Login.jsp
   *       if (custom Login.jsp exists)
   *         use custom Login.jsp
   *       else
   *         use wdk login page name
   *         
   * New logic for resulting page:
   *   check for referrer-form as request parameters
   *   read referrer on request (submitting page)
   *   if (user is logged in or successful login)
   *     redirect = true
   *     if (referrer-form exists)
   *       use referrer-form
   *     else
   *       use referrer
   *   else
   *     redirect = false
   *     use login page name
   *     if (referrer-form exists)
   *       set referrer-form on request
   *     else
   *       set referrer on request
   */
  @Override
  protected ActionResult handleRequest(ParamGroup params) throws Exception {

    // get the current user
    UserBean currentUser = getCurrentUser();

    if (!currentUser.isGuest()) {
      // then user is already logged in
      return new ActionResult().setViewName(SUCCESS);
    }
    else {
      return handleLogin(params, currentUser, getWdkModel().getUserFactory());
    }
  }

  private ActionResult handleLogin(ParamGroup params, UserBean guest, UserFactoryBean factory) throws WdkModelException {
    AuthenticationMethod authMethod = getWdkModel().getModel().getModelConfig().getAuthenticationMethodEnum();
    switch (authMethod) {
      case OAUTH2:
        return handleOauthLogin(params, guest, factory);
      case USER_DB:
        return handleUserDbLogin(params, guest, factory);
      default:
        throw new WdkModelException("Login action does not yet handle authentication method " + authMethod);
    }
  }

  private ActionResult handleOauthLogin(ParamGroup params, UserBean guest, UserFactoryBean factory) {

    // check for error or to see if user denied us access; they won't be able to log in
    String error = params.getValueOrDefault("error", null);
    if (error != null) {
      String message = (error.equals("access_denied") ?
        "You did not grant permission to access identifying information so we cannot log you in." :
        "An error occurred [" + error + "] on the authentication server and you cannot log in at this time.");
      return getFailedLoginResult(new WdkUserException(message));
    }

    // confirm state token for security, then remove regardless of result
    String stateToken = params.getValue("state");
    String storedStateToken = (String)getSessionAttribute(OAuthUtil.STATE_TOKEN_KEY);
    unsetSessionAttribute(OAuthUtil.STATE_TOKEN_KEY);
    if (stateToken == null || storedStateToken == null || !stateToken.equals(storedStateToken)) {
      return getFailedLoginResult(new WdkModelException("Unable to log in; " +
          "state token missing, incorrect, or expired.  Please try again."));
    }

    ModelConfig modelConfig = getWdkModel().getModel().getModelConfig();

    // params used to fetch token and validate user
    String authCode = params.getValue("code");
    String redirectUrl = params.getValueOrDefault("redirectUrl",
        modelConfig.getWebAppUrl() + "/home.do");

    try {
      OAuthClient client = new OAuthClient(modelConfig, factory);
      int userId = client.getUserIdFromAuthCode(authCode);

      UserBean user = factory.login(guest, userId);
      if (user == null) throw new WdkModelException("Unable to find user with ID " +
          userId + ", returned by OAuth service for authCode " + authCode);

      LOG.info("Successfully found user with ID: " + user.getUserId() + " and email '" + user.getEmail() + "'. redirectUrl=" + redirectUrl);

      int wdkCookieMaxAge = addLoginCookie(user, true, getWdkModel(), this);
      setCurrentUser(user);
      setSessionAttribute(CConstants.WDK_LOGIN_ERROR_KEY, "");
      // go back to user's original page after successful login
      return getSuccessfulLoginResult(redirectUrl, wdkCookieMaxAge);
    }
    catch (Exception e) {
      LOG.error("Could not log user in with authCode " + authCode, e);
      return getFailedLoginResult(e);
    }
  }

  private ActionResult handleUserDbLogin(ParamGroup params, UserBean guest, UserFactoryBean factory) {
    // get user's input
    String email = params.getValue(CConstants.WDK_EMAIL_KEY);
    String password = params.getValue(CConstants.WDK_PASSWORD_KEY);
    boolean remember = params.getSingleCheckboxValue(REMEMBER_PARAM_KEY);
    
    // authenticate
    try {
      UserBean user = factory.login(guest, email, password);
      int wdkCookieMaxAge = addLoginCookie(user, remember, getWdkModel(), this);
      setCurrentUser(user);
      setSessionAttribute(CConstants.WDK_LOGIN_ERROR_KEY, "");
      // go back to user's original page after successful login
      String redirectPage = getOriginalReferrer(params, getRequestData());
      return getSuccessfulLoginResult(redirectPage, wdkCookieMaxAge);
    }
    catch (Exception ex) {
      LOG.info("Could not authenticate user's identity.  Exception thrown: ", ex);
      // user authentication failed, set the error message and the referring page
      return getFailedLoginResult(ex);
    }
  }

  /**
   * Return action result for successful login
   * 
   * @param redirectUrl URL to which user will be directed
   * @param wdkCookieMaxAge max age of login cookie (if applicable)
   * @return action result
   */
  protected ActionResult getSuccessfulLoginResult(String redirectUrl, int wdkCookieMaxAge) {
    if (redirectUrl.isEmpty()) redirectUrl = "/home.do";
    return new ActionResult().setRedirect(true).setViewPath(redirectUrl);
  }

  protected ActionResult getFailedLoginResult(Exception ex) {
    ActionResult result = new ActionResult()
      .setRequestAttribute(CConstants.WDK_LOGIN_ERROR_KEY, ex.getMessage())
      .setRequestAttribute(CConstants.WDK_REDIRECT_URL_KEY, getOriginalReferrer(getParams(), getRequestData()));

    String customViewFile = getCustomViewDir() + CConstants.WDK_LOGIN_PAGE;
    if (wdkResourceExists(customViewFile)) {
      return result.setRedirect(false).setViewPath(customViewFile);
    }
    else {
      return result.setViewName(INPUT);
    }
  }
  
  public static String getOriginalReferrer(ParamGroup params, RequestData requestData) {
    
    // always prefer the param value passed to us from a previous action
    String referrerParamValue = params.getValueOrEmpty(CConstants.WDK_REDIRECT_URL_KEY);
    if (!referrerParamValue.isEmpty()) {
      return referrerParamValue;
    }
      
    // if no referrer exists, then (presumably) user failed authentication, but in case they got
    // to the login page via bookmark or some other way, return to profile on successful login
    String referrer = requestData.getReferrer();
    if (referrer == null) {
      referrer = "home.do";
    }
    else if (referrer.indexOf("showLogin.do") != -1 ||
             referrer.indexOf("processLogin.do") != -1) {
        return "showProfile.do";
    }

    // otherwise, return referring page
    return referrer;
  }

  @SuppressWarnings("unused")
  private void checkExistenceOfParams(ParamGroup params) throws WdkUserException {
    // must make sure user submitted username/password
    String email = params.getValue(CConstants.WDK_EMAIL_KEY);
    String password = params.getValue(CConstants.WDK_PASSWORD_KEY);
    if (email == null || email.length() == 0 || password == null || password.length() == 0) {
      throw new WdkUserException("You must enter both username and password.");
    }
  }
  
  static int addLoginCookie(UserBean user, boolean remember, WdkModelBean model, WdkAction wdkAction) {
    try {
      // Create & send cookie
      LoginCookieFactory auth = new LoginCookieFactory(model.getModel().getSecretKey());
      Cookie loginCookie = auth.createLoginCookie(user.getEmail(), remember);
      wdkAction.addCookieToResponse(loginCookie);
      return loginCookie.getMaxAge();
    }
    catch (WdkModelException e) {
      // This is a recoverable exception since this is not the session cookie, just
      //   one to remember the user.  An error, but not one we need to advertise
      LOG.error("Unable to add user cookie to response on login!", e);
      return -1;
    }
  }
}
