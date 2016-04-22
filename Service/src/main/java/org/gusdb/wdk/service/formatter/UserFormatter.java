package org.gusdb.wdk.service.formatter;

import java.util.Map;
import java.util.Map.Entry;

import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.user.User;
import org.gusdb.wdk.service.formatter.Keys;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Formats WDK User profile and preference data.  User profile JSON will have
 * the following form:
 * 
 * {
 *   id: Integer,
 *   firstName: String,
 *   middleName: String,
 *   lastName: String,
 *   organization: String,
 *   email: String (private),
 *   properties: Object (private)
 * }
 * 
 * User preferences are formatted as a JSON Object representing a map from
 * preference key (String) to preference value (String)
 * 
 * @author rdoherty
 */
public class UserFormatter {

  public static JSONObject getUserJson(User user, boolean isOwner, boolean includePreferences) throws JSONException, WdkModelException {
    JSONObject json = new JSONObject()
      .put(Keys.ID, user.getUserId())
      .put(Keys.FIRST_NAME, user.getFirstName())
      .put(Keys.MIDDLE_NAME, user.getMiddleName())
      .put(Keys.LAST_NAME, user.getLastName())
      .put(Keys.TITLE, user.getTitle())
      .put(Keys.DEPARTMENT, user.getDepartment())
      .put(Keys.ORGANIZATION, user.getOrganization())
      .put(Keys.ADDRESS, user.getAddress())
      .put(Keys.CITY, user.getCity())
      .put(Keys.STATE, user.getState())
      .put(Keys.COUNTRY, user.getCountry())
      .put(Keys.ZIP_CODE, user.getZipCode())
      .put(Keys.PHONE_NUMBER, user.getPhoneNumber())
      .put(Keys.IS_GUEST, user.isGuest());
    // private fields viewable only by owner
    if (isOwner) {
      json.put(Keys.EMAIL, user.getEmail())
          .put(Keys.APPLICATION_SPECIFIC_PROPERTIES, getUserPrefsJson(user.getGlobalPreferences()));
      if (includePreferences) {
        json.put(Keys.PREFERENCES, getUserPrefsJson(user.getProjectPreferences()));
      }
    }
    return json;
  }

  public static JSONObject getUserPrefsJson(Map<String,String> preferenceMap) {
    JSONObject prefs = new JSONObject();
    for (Entry<String,String> pref : preferenceMap.entrySet()) {
      prefs.put(pref.getKey(), pref.getValue());
    }
    return prefs;
  }

}
