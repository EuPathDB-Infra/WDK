package org.gusdb.wdk.service.service.user;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.gusdb.fgputil.Tuples.TwoTuple;
import org.gusdb.fgputil.functional.Functions;
import org.gusdb.fgputil.validation.ValidObjectFactory.RunnableObj;
import org.gusdb.fgputil.validation.ValidationLevel;
import org.gusdb.wdk.core.api.JsonKeys;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.answer.AnswerValue;
import org.gusdb.wdk.model.answer.factory.AnswerValueFactory;
import org.gusdb.wdk.model.answer.request.AnswerFormattingParser;
import org.gusdb.wdk.model.answer.request.AnswerRequest;
import org.gusdb.wdk.model.answer.spec.AnswerSpecBuilder;
import org.gusdb.wdk.model.user.NoSuchElementException;
import org.gusdb.wdk.model.user.Step;
import org.gusdb.wdk.model.user.Step.StepBuilder;
import org.gusdb.wdk.model.user.StepFactory;
import org.gusdb.wdk.model.user.StepFactoryHelpers.UserCache;
import org.gusdb.wdk.model.user.User;
import org.gusdb.wdk.service.annotation.InSchema;
import org.gusdb.wdk.service.annotation.OutSchema;
import org.gusdb.wdk.service.annotation.PATCH;
import org.gusdb.wdk.service.formatter.StepFormatter;
import org.gusdb.wdk.service.request.exception.ConflictException;
import org.gusdb.wdk.service.request.exception.DataValidationException;
import org.gusdb.wdk.service.request.exception.RequestMisformatException;
import org.gusdb.wdk.service.request.strategy.StepRequest;
import org.gusdb.wdk.service.service.AbstractWdkService;
import org.gusdb.wdk.service.service.AnswerService;
import org.json.JSONException;
import org.json.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.gusdb.wdk.model.answer.request.AnswerFormattingParser.DEFAULT_REPORTER_PARSER;
import static org.gusdb.wdk.model.answer.request.AnswerFormattingParser.SPECIFIED_REPORTER_PARSER;

public class StepService extends UserService {

  public static final String STEP_RESOURCE = "Step ID ";

  public StepService(@PathParam(USER_ID_PATH_PARAM) String uid) {
    super(uid);
  }

  @POST
  @Path("steps")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @InSchema("wdk.users.steps.post-request")
  @OutSchema("wdk.users.steps.post-response")
  public Response createStep(JSONObject jsonBody)
  throws WdkModelException, DataValidationException
  {
    try {
      User user = getUserBundle(Access.PRIVATE).getSessionUser();
      StepRequest stepRequest = StepRequest.newStepFromJson(jsonBody, getWdkModel(), user);

      Step step = getWdkModel().getStepFactory()
        .createStep(
          user, stepRequest.getAnswerSpec(), filters, filterOptions,
          assignedWeight, false, stepRequest.getCustomName(),
          stepRequest.isCollapsible(), stepRequest.getCollapsedName(), strategy);

      return Response.ok(new JSONObject().put(JsonKeys.ID, step.getStepId()))
          .location(getUriInfo().getAbsolutePathBuilder().build(step.getStepId()))
          .build();
    }
    catch (JSONException | RequestMisformatException e) {
      throw new BadRequestException(e);
    }
  }

  @GET
  @Path("steps/{stepId}")
  @Produces(MediaType.APPLICATION_JSON)
  @OutSchema("wdk.users.steps.id.get-response")
  public JSONObject getStep(
    @PathParam("stepId") long stepId,
    @QueryParam("validationLevel") String validationLevelStr
  ) throws WdkModelException {
    ValidationLevel validationLevel = Functions.defaultOnException(
      () -> ValidationLevel.valueOf(validationLevelStr),
      ValidationLevel.SEMANTIC);

    return StepFormatter.getStepJsonWithEstimatedSize(getStepForCurrentUser(
        stepId, validationLevel));
  }

  /**
   * TODO: Why does the patch endpoint have a response body?
   *
   * @param stepId Database ID of the step to update
   * @param body   Json body containing only updated fields for a step.
   */
  @PATCH
  @Path("steps/{stepId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @InSchema("wdk.users.steps.id.patch-request")
  public Response updateStepMeta(@PathParam("stepId") long stepId,
      JSONObject body) throws WdkModelException {

    // Nothing to do.
    if (body.length() == 0)
      return Response.ok().build();

    try {
      final Step step = updateStepMeta(getStepForCurrentUser(stepId, ValidationLevel.NONE), body);

      getWdkModel().getStepFactory()
          .updateStep(step);

      // return updated step
      return Response.ok(StepFormatter.getStepJsonWithEstimatedSize(step)).build();
    }
    catch (JSONException e) {
      throw new BadRequestException(e);
    }
  }

  @DELETE
  @Path("steps/{stepId}")
  public void deleteStep(@PathParam("stepId") long stepId)
      throws WdkModelException, ConflictException {

    Step step = getStepForCurrentUser(stepId, ValidationLevel.NONE);
    if (step.isDeleted())
      throw new NotFoundException(
          AbstractWdkService.formatNotFound(STEP_RESOURCE + stepId));

    if (step.getStrategy() != null)
      throw new ConflictException(
          "Steps that are part of strategies cannot be " +
              "deleted.  Remove the step from strategy " +
              step.getStrategyId() + " and try again.");

    getWdkModel().getStepFactory()
        .updateStep(Step.builder(step)
            .setDeleted(true)
            .build(new UserCache(step.getUser()), ValidationLevel.NONE, null)
        );
  }

  @POST
  @Path("steps/{stepId}/answer")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @InSchema("wdk.users.steps.answer.post-request")
  public Response createDefaultReporterAnswer(@PathParam("stepId") long stepId, String body)
      throws WdkModelException, RequestMisformatException, DataValidationException {
    return createAnswer(stepId, body, DEFAULT_REPORTER_PARSER);
  }

  @POST
  @Path("steps/{stepId}/answer/report")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response createAnswer(@PathParam("stepId") long stepId, String body)
      throws WdkModelException, RequestMisformatException, DataValidationException {
    return createAnswer(stepId, body, SPECIFIED_REPORTER_PARSER);
  }

  @PUT
  @Path("steps/{stepId}/answerSpec")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  // TODO: @InSchema(...)
  public void putAnswerSpec(
      @PathParam("stepId") long stepId,
      ObjectNode body
  ) throws WdkModelException {
    final Step step = getStepForCurrentUser(stepId, validationLevel);
    final AnswerSpecBuilder spec; // TODO: How to populate this from body?

    Step.builder(step)
      .setAnswerSpec(spec)
      .build(new UserCache(getUserBundle(Access.PRIVATE).getSessionUser()),
          validationLevel, step.getStrategy());
  }

  @GET
  @Path("steps/{stepId}/answer/filter-summary/{filterName}")
  @Produces(MediaType.APPLICATION_JSON)
  public JSONObject getFilterSummary(
    @PathParam("stepId") long stepId,
    @PathParam("filterName") String filterName
  ) throws WdkModelException, DataValidationException {
    return AnswerValueFactory.makeAnswer(
        getUserBundle(Access.PRIVATE).getSessionUser(),
        Step.getRunnableAnswerSpec(
          getStepForCurrentUser(stepId, ValidationLevel.RUNNABLE)
            .getRunnable()
            .getOrThrow(StepService::getNotRunnableException)))
      .getFilterSummaryJson(filterName);
  }

  private static DataValidationException getNotRunnableException(Step badStep) {
    return new DataValidationException(
        "This step is not runnable for the following reasons: " + badStep.getValidationBundle().toString());
  }

  private Response createAnswer(long stepId, String requestBody, AnswerFormattingParser formattingParser)
      throws WdkModelException, RequestMisformatException, DataValidationException {
    try {
      User user = getUserBundle(Access.PRIVATE).getSessionUser();
      StepFactory stepFactory = new StepFactory(getWdkModel());
      RunnableObj<Step> runnableStep = stepFactory
          .getStepById(stepId, ValidationLevel.RUNNABLE)
          .orElseThrow(NotFoundException::new)
          .getRunnable()
          .getOrThrow(StepService::getNotRunnableException);

      AnswerRequest request = new AnswerRequest(
          Step.getRunnableAnswerSpec(runnableStep),
          formattingParser.createFromTopLevelObject(requestBody));
      TwoTuple<AnswerValue, Response> result = AnswerService.getAnswerResponse(user, request);

      // TODO: get result size from answer value, write it as estimated size, and update last_run

      return result.getSecond();
    }
    catch (JSONException e) {
      throw new RequestMisformatException(e.getMessage());
    }
  }

  private Step updateStepMeta(Step step, JSONObject req)
      throws WdkModelException {

    StepBuilder newStep = Step.builder(step);

    if(req.has(JsonKeys.CUSTOM_NAME))
      newStep.setCustomName(req.getString(JsonKeys.CUSTOM_NAME));
    if(req.has(JsonKeys.IS_COLLAPSIBLE))
      newStep.setCollapsible(req.getBoolean(JsonKeys.IS_COLLAPSIBLE));
    if(req.has(JsonKeys.COLLAPSED_NAME))
      newStep.setCollapsedName(req.getString(JsonKeys.COLLAPSED_NAME));

    return newStep.build(new UserCache(step.getUser()),
        step.getValidationBundle().getLevel(), step.getStrategy());
  }

  private Step getStepForCurrentUser(long stepId, ValidationLevel level) throws WdkModelException {
    try {
      User user = getUserBundle(Access.PRIVATE).getSessionUser();
      Step step = getWdkModel().getStepFactory()
          .getStepById(stepId, level)
          .orElseThrow(() -> new NoSuchElementException("Cannot find step with ID " + stepId));

      if (step.getUser().getUserId() != user.getUserId())
        throw new ForbiddenException(AbstractWdkService.PERMISSION_DENIED);

      return step;
    }
    catch (NoSuchElementException e) {
      throw new NotFoundException(AbstractWdkService.formatNotFound(STEP_RESOURCE + stepId));
    }
  }
}
