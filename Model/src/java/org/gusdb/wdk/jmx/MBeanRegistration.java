package org.gusdb.wdk.jmx;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.servlet.ServletContext;

import org.apache.log4j.Logger;

/**
 * Registers mbeans named in MBeanSet .
 */
public class MBeanRegistration {

  private static final Logger logger = Logger.getLogger(MBeanRegistration.class);
  private MBeanServer server;
  private List<ObjectName> registeredMBeans = new ArrayList<ObjectName>();
  
  private Map<String, String> mbeanClassNames = MBeanSet.getMbeanClassMapping();


  public MBeanRegistration() {}

  /** 
   * Initialization of MBean server and registration of MBeans.
   */
  public void init() {
    server = getMBeanServer();
    registerMBeans();  
  }

  /** 
   * Unregister mbeans and cleanup
   */
  public void destroy() {
      unregisterMBeans();
  }
  
  /**
   * Return the virtual hostname defined for the Tomcat context.
   * This hostname is used for MBean object naming
   */
  private String getHostName() {
    // temp method until getHostNameBROKEN_IN_TC5() can be fixed
    return "localhost";
  }
  
  /** 
   * Unused method that will replace getHostname() .
   * Tomcat 5 throws
   * java.lang.ClassNotFoundException: org.apache.catalina.core.StandardContext
   * with the following code. This class is in $CATALINA_BASE/server/lib/catalina.jar
   * 
   * The host lookup works in TC 6.0.33
   * The hostname is hardcoded in getHostName() until Tomcat 5 support
   * is dropped.
   */
  @SuppressWarnings("unused")
  private String getHostNameBROKEN_IN_TC5() {
  
     /**
       * I can't find a direct way to get the Host we are deploying into. I did find
       * this technique in
       * fr.xebia.management.ServletContextAwareMBeanServerFactory
       */
    String hostName = null;
    try {
      ServletContext servletContext = ContextThreadLocal.get();
      
      Field standardContextHostNameField = Class.forName("org.apache.catalina.core.StandardContext").getDeclaredField("hostName");
      standardContextHostNameField.setAccessible(true);

      Field applicationContextFacadeContextField = Class.forName("org.apache.catalina.core.ApplicationContextFacade").getDeclaredField("context");
      applicationContextFacadeContextField.setAccessible(true);
      
      Field applicationContextContextField = Class.forName("org.apache.catalina.core.ApplicationContext").getDeclaredField("context");
      applicationContextContextField.setAccessible(true);

      Object applicationContext = applicationContextFacadeContextField.get(servletContext);
      Object standardContext = applicationContextContextField.get(applicationContext);
      hostName = (String) standardContextHostNameField.get(standardContext);
    } catch (Exception e) {
      logger.error(e);
    }
    return hostName;
  }
  
  /**
   * Do registration of set of MBeans in server.
   *
   * @see MBeanSet
   */
  private void registerMBeans() {
    try {
      for (Map.Entry<String, String> entry : mbeanClassNames.entrySet()) {
      //for (String name : mbeanClassNames) {
        Object mb = getMbeanClassForName(entry.getKey());
        if (mb == null) {
           logger.warn("Unable to instantiate class for " + entry.getKey() + " . Skipping.");
           continue;
        }
        ObjectName objectName = makeObjectName(entry.getValue());
        registerMBean(mb, objectName);
      }
    } catch (MalformedObjectNameException mone) {
        throw new RuntimeException(mone);
    } catch (MBeanRegistrationException mbre) {
        throw new RuntimeException(mbre);    
    } catch (InstanceAlreadyExistsException iaee) {
        logger.error(iaee);
    } catch (NotCompliantMBeanException ncmbe) {
        throw new RuntimeException(ncmbe);
    }
  }

  /**
   * Do registration of a single MBeans in server.
   *
   * @see MBeanSet
   */
  private void registerMBean(Object pObject, ObjectName pName)
    throws MalformedObjectNameException, MBeanRegistrationException, InstanceAlreadyExistsException, NotCompliantMBeanException {
    logger.debug("registering mbean " + pName.toString());
    server.registerMBean(pObject, pName);
    registeredMBeans.add(pName);
  }

  /**
   * Unregistration set of MBeans in server.
   *
   * @see MBeanSet
   */
  private void unregisterMBeans() {
    for (ObjectName name : registeredMBeans) {
      try {
        logger.debug("unregistering mbean " + name.toString());
        server.unregisterMBean(name);
      } catch (InstanceNotFoundException infe) {
        logger.warn("Exception while unregistering " + name + " " + infe);
      } catch (MBeanRegistrationException mbre) {
        logger.warn("Exception while unregistering " + name + " " + mbre);
      }
    }
  }

  /**
   * Return class instance for given class name.
   *
   * @param classname Name of class to instantiate
   * @return Class instance
   */
  private Object getMbeanClassForName(String classname) {
    Object bean = null;
    Class<?> clazz = getClass(classname);
    if (clazz == null) return null;
    try {
      Constructor<?> con = clazz.getConstructor();
      bean = con.newInstance();
    } catch (InstantiationException ie) {
        logger.warn(ie);
    } catch (IllegalAccessException iae) {
        logger.warn(iae);
    } catch (InvocationTargetException ite) {
        logger.warn(ite);
    } catch (NoSuchMethodException nsme) {
        logger.warn(nsme);
    }
    return bean;
  }

  /**
   * Returns the Class object associated with the class or interface with the given string name.
   *
   * @param classname Name of class
   * @return MBean object
   */
  private Class<?> getClass(String classname) {
    try {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      return Class.forName(classname, false, loader);
    } catch (ClassNotFoundException cnfe) {
      logger.warn(cnfe);
    }
    return null;
  }

  /**
   * Construct an MBean object name from a precussor name.
   * 
   * @param precursor object name
   * @return MBean ObjecName
   */
  private ObjectName makeObjectName(String pObjectNameString) throws MalformedObjectNameException, NullPointerException { 
    String path = getContextPath();
    String host = getHostName();
    String objectNameString = pObjectNameString + ",path=//" + host + path;
    return new ObjectName(objectNameString);
  }

  /**
   * Return the application context path
   *
   * @reutrn context path
   */
  private String getContextPath() {
    ServletContext sc = ContextThreadLocal.get();
    String contextName = null;

    if (sc.getMajorVersion() > 2 || sc.getMajorVersion() == 2 && sc.getMinorVersion() >= 5) {
      // Servlet API is >= 2.5 and has ServletContext getContextPath() method but 
      // we have to make an indiret method call so code compiles for API < 2.5
      try {
        Method m = sc.getClass().getMethod("getContextPath", new Class[] {});
        contextName = (String) m.invoke(sc, (Object[]) null);
      } catch (SecurityException se) {
        throw new RuntimeException(se);
      } catch (NoSuchMethodException nsme) {
        throw new RuntimeException(nsme);
      } catch (IllegalArgumentException iae) {
        throw new RuntimeException(iae);
      } catch (IllegalAccessException iae) {
        throw new RuntimeException(iae);
      } catch (InvocationTargetException ite) {
        throw new RuntimeException(ite);
      }
    } else {
      // hack for old servlet API: use the name of the tempdir
      String tmpdir = ((java.io.File) sc.getAttribute("javax.servlet.context.tempdir")).getName();
      contextName = "/" + tmpdir;
    }

    return contextName;
  }

  /**
   * Return current MBeanServer, creating one if an existing one is not found.
   */
  private MBeanServer getMBeanServer() {
    MBeanServer mbserver = null;

    ArrayList<MBeanServer> mbservers = MBeanServerFactory.findMBeanServer(null);

    if (mbservers.size() > 0) {
      mbserver = mbservers.get(0);
    }

    if (mbserver == null) {
      mbserver = MBeanServerFactory.createMBeanServer();
    } 

    return mbserver;
  }


}