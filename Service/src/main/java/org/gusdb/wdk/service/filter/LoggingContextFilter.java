package org.gusdb.wdk.service.filter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;

import org.glassfish.grizzly.http.server.Request;
import org.gusdb.fgputil.logging.ThreadLocalLoggingVars;
import org.gusdb.fgputil.web.RequestData;
import org.gusdb.wdk.controller.ContextLookup;

@PreMatching
@Priority(30)
public class LoggingContextFilter implements ContainerRequestFilter, WriterInterceptor {

  private static final AtomicInteger requestId = new AtomicInteger(1);

  @Inject
  private Provider<HttpServletRequest> _servletRequest;

  @Inject
  private Provider<Request> _grizzlyRequest;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {

    RequestData request = ContextLookup.getRequest(_servletRequest.get(), _grizzlyRequest.get());

    ThreadLocalLoggingVars.setRequestStartTime(System.currentTimeMillis());
    ThreadLocalLoggingVars.setIpAddress(request.getRemoteIpAddress());
    ThreadLocalLoggingVars.setRequestedDomain(request.getServerName());
    ThreadLocalLoggingVars.setRequestId(String.valueOf(requestId.getAndIncrement()));

  }

  @Override
  public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
    context.proceed();
    ThreadLocalLoggingVars.clearValues();
  }

}
