package org.gusdb.wdk.client;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.glassfish.jersey.server.mvc.ErrorTemplate;
import org.glassfish.jersey.server.mvc.Template;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Returns HTML by rendering the index template, for all HTML paths.
 * 
 * For POST requests, the "payload" parameter is parsed as a JSONObject and
 * passed to the template as the model.
 *
 *
 * @author dfalke
 *
 */
@Path("/{any: .*}")
@Produces("text/html")
public class WdkClient {
  
  private static final String PAYLOAD_PARAM = "payload";
  private static final Logger LOG = Logger.getLogger(WdkClient.class);

  @GET
  @Template(name = "/index")
  public String get() {
    return parsePayload(null);
  }

  // FIXME Error handling
  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Template(name = "/index")
  @ErrorTemplate(name = "/error")
  public String post(@FormParam(PAYLOAD_PARAM) String payload) {
   return parsePayload(payload);
  }
  
  private static String parsePayload(String payload) {
    try {
      return isEmpty(payload) ? "null"
          : new JSONObject(new JSONTokener(payload)).toString();
    } catch (JSONException e) {
      LOG.debug("POST payload parameter is not valid JSON", e);
      throw new BadRequestException();
    }

  }
  
  private static boolean isEmpty(String string) {
    return (string == null || string.isEmpty() || string.equals("undefined"));
  }

}