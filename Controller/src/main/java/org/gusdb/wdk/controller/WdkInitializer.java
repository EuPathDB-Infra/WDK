package org.gusdb.wdk.controller;

import static org.gusdb.wdk.model.ThreadMonitor.getThreadMonitorConfig;

import javax.servlet.ServletContext;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.runtime.GusHome;
import org.gusdb.wdk.controller.wizard.Wizard;
import org.gusdb.wdk.model.MDCUtil;
import org.gusdb.wdk.model.ThreadMonitor;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.jspwrap.WdkModelBean;

public class WdkInitializer {

  private static final Logger LOG = Logger.getLogger(ApplicationInitListener.class);

  public static void initializeWdk(ServletContext servletContext) {
    try {
      MDCUtil.setNonRequestThreadVars("init");
      LOG.info("Initializing WDK web application");

      // get gus home and set on context
      String gusHome = GusHome.webInit(servletContext);
      servletContext.setAttribute(CConstants.GUS_HOME_KEY, gusHome);

      LOG.info("Initializing model...");
      String projectId = servletContext.getInitParameter(Utilities.ARGUMENT_PROJECT_ID);
      WdkModel wdkModel = WdkModel.construct(projectId, gusHome);
      WdkModelBean wdkModelBean = new WdkModelBean(wdkModel);

      LOG.info("Initialized model object.  Setting on servlet context.");
      servletContext.setAttribute(CConstants.WDK_MODEL_KEY, wdkModelBean);

      // Set assetsUrl attribute. It will be null if not defined in the model
      servletContext.setAttribute(CConstants.WDK_ASSETS_URL_KEY,
          wdkModel.getModelConfig().getAssetsUrl());

      // assign select init parameters as context attributes
      assignInitParamToAttribute(servletContext, CConstants.WDK_ALWAYSGOTOSUMMARY_KEY);
      assignInitParamToAttribute(servletContext, CConstants.WDK_LOGIN_URL_KEY);

      // load wizard
      LOG.info("Loading wizard configuration.");
      Wizard wizard = Wizard.loadWizard(gusHome, wdkModelBean);
      servletContext.setAttribute(CConstants.WDK_WIZARD_KEY, wizard);

      // start up thread monitor
      ThreadMonitor.start(getThreadMonitorConfig(wdkModel));

      LOG.info("WDK web application initialization complete.");
    }
    catch (Exception ex) {
      throw new RuntimeException(ex);
    }
    finally {
      MDCUtil.clearValues();
    }
  }

  public static void terminateWdk(ServletContext servletContext) {
    try {
      MDCUtil.setNonRequestThreadVars("term");
      LOG.info("Terminating WDK web application");

      // shut down thread monitor
      ThreadMonitor.shutDown();

      WdkModelBean wdkModel = getWdkModel(servletContext);
      if (wdkModel != null) {
        // insulate in case model never properly loaded
        LOG.info("Releasing resources for WDK Model.");
        wdkModel.getModel().releaseResources();
        LOG.info("WDK resource release complete.");
      }
    }
    catch (Exception ex) {
      throw new RuntimeException(ex);
    }
    finally {
      MDCUtil.clearValues();
    }
  }

  public static WdkModelBean getWdkModel(ServletContext servletContext) {
    return (WdkModelBean)servletContext.getAttribute(CConstants.WDK_MODEL_KEY);
  }

  private static void assignInitParamToAttribute(ServletContext servletContext, String key) {
    servletContext.setAttribute(key, servletContext.getInitParameter(key));
  }
}
