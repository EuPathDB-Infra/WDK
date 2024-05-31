package org.gusdb.wdk.controller;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

import org.gusdb.fgputil.web.servlet.HttpServletApplicationContext;

/**
 * A class that is initialized at the start of the web application. This makes
 * sure global resources are available to all the contexts that need them
 */
public class ApplicationInitListener implements ServletContextListener {

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    WdkInitializer.initializeWdk(new HttpServletApplicationContext(sce.getServletContext()));
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
    WdkInitializer.terminateWdk(new HttpServletApplicationContext(sce.getServletContext()));
  }
}
