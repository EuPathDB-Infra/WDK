package org.gusdb.wdk.service.service;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.json.JsonUtil;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.ontology.Ontology;
import org.gusdb.wdk.model.ontology.PropertyPredicate;
import org.gusdb.wdk.service.formatter.OntologyFormatter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@Path("/ontologies")
@Produces(MediaType.APPLICATION_JSON)
public class OntologyService extends AbstractWdkService {

  @SuppressWarnings("unused")
  private static final Logger LOG = Logger.getLogger(OntologyService.class);
  private static final String ONTOLOGY_RESOURCE = "Ontology Name: ";

  private static final String CATEGORIES_ONTOLOGY_ALIAS = "__wdk_categories__";

  /**
   * Get a list of all ontologies (names)
   */
  @GET
  public Response getOntologies() throws JSONException {
    return Response.ok(FormatUtil.stringCollectionToJsonArray(
        getWdkModel().getOntologyNames()).toString()).build();
  }

  /**
   * Get the information about a specific ontology.
   */
  @GET
  @Path("{ontologyName}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getOntologyByName(@PathParam("ontologyName") String ontologyName) throws WdkModelException {
    Ontology ontology = getOntology(ontologyName);
    if (ontology == null)
      throw new NotFoundException(AbstractWdkService.formatNotFound(ONTOLOGY_RESOURCE + ontologyName));
    return Response.ok(OntologyFormatter.getOntologyJson(ontology).toString()).build();
  }

  @POST
  @Path("{ontologyName}/path")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response getPathsToMatchingNodes(@PathParam("ontologyName") String ontologyName, String body) throws WdkModelException {
    Ontology ontology = getOntology(ontologyName);
    if (ontology == null)
      throw new NotFoundException(AbstractWdkService.formatNotFound(ONTOLOGY_RESOURCE + ontologyName));
    try {
      JSONObject criteriaJson = new JSONObject(body);
      Map<String,String> criteria = new HashMap<String,String>();
      for (String key : JsonUtil.getKeys(criteriaJson)) {
        criteria.put(key, criteriaJson.getString(key));
      }
      JSONArray pathsList = OntologyFormatter.pathsToJson(
          ontology.getAllPaths(new PropertyPredicate(criteria)));
      return Response.ok(pathsList.toString()).build();
    }
    catch (JSONException e) {
      throw new BadRequestException(e);
    }
  }

  private Ontology getOntology(String ontologyName) throws WdkModelException {
    WdkModel model = getWdkModel();
    if (CATEGORIES_ONTOLOGY_ALIAS.equals(ontologyName)) {
      ontologyName = model.getCategoriesOntologyName();
    }
    return model.getOntology(ontologyName);
  }

}
