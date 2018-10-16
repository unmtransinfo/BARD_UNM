package edu.unm.health.biocomp.bard.helloinit;

import java.io.*;
import java.util.*;
import java.net.*; // MalformedURLException, URI, URISyntaxException, URL, URLEncoder
import java.sql.*;

import javax.servlet.*; // ServletConfig, ServletContext, ServletContextEvent, ServletContextListener
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*; // GET, POST, Path, PathParam, QueryParam, FormParam, Produces, WebApplicationException
import javax.ws.rs.core.*; // Response, MediaType

import gov.nih.ncgc.bard.rest.BARDConstants;
import gov.nih.ncgc.bard.tools.*; // Util, PluginValidator
import gov.nih.ncgc.bard.plugin.*; // IPlugin, PluginManifest

/**	BARD plugin helloinit (world) from UNM.
	Hello world, with one-time initialization at servlet instantiation.

	@author Jeremy Yang
*/
@Path("/helloinit")
public class helloinit_bardPlugin implements IPlugin, ServletContextListener
{
  private static final String PLUGIN_VERSION="0.9beta";
  private static String HELLO_NOTE=null;		// configured via web.xml
  private static java.util.Date initdate = null; //initialized once per servlet deployment in contextInitialized()
  private static java.util.Date usedate = null; //initialized once per object instantiation in constructor

  public helloinit_bardPlugin() // for validation only
  {
  }

  public helloinit_bardPlugin(
  	@Context ServletConfig servletConfig,
  	@Context ServletContext servletContext,
	@Context HttpServletRequest httpServletRequest,
	@Context HttpHeaders headers)
  {
    HELLO_NOTE=servletConfig.getInitParameter("HELLO_NOTE");
    if (HELLO_NOTE==null) HELLO_NOTE="none (hard-coded default)";
    usedate = new java.util.Date();
  }

  /**	Required method.	*/
  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @Path("/_version")
  public String getVersion() { return PLUGIN_VERSION; }

  /**	Required method.	*/
  @GET
  @Path("/_manifest")
  @Produces(MediaType.APPLICATION_JSON)
  public String getManifest()
  {
    PluginManifest pm = new PluginManifest();
    pm.setAuthor("Jeremy Yang");
    pm.setAuthorEmail("jjyang@salud.unm.edu");
    pm.setMaintainer(pm.getAuthor());
    pm.setMaintainerEmail(pm.getAuthorEmail());
    pm.setTitle("Hello from UNM minimal plugin");
    pm.setDescription("A brief description will go here");
    pm.setVersion(PLUGIN_VERSION);

    PluginManifest.PluginResource res1 = new PluginManifest.PluginResource();
    res1.setPath("/test");
    res1.setMimetype("text/plain");
    res1.setMethod("GET");
    pm.setResources(new PluginManifest.PluginResource[]{res1});

    return pm.toJson();
  }

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @Path("/test")
  public String test() { return "Testing 1, 2, 3..."; }

  /**	Required method.	*/
  public String[] getResourcePaths()
  {
    List<String> paths=Util.getResourcePaths(this.getClass());
    String[] ret = new String[paths.size()];
    for (int i=0; i<paths.size(); ++i)
      ret[i]=BARDConstants.API_BASE+paths.get(i);
    return ret;
  }

  /**	Required method.	*/
  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @Path("/_info")
  public String getDescription()
  {
    StringBuilder msg = new StringBuilder("HELLO, from UNM.\n");
    msg.append("HELLO_NOTE: "+HELLO_NOTE+"\n");
    msg.append("initdate: "+((initdate==null)?"null":initdate.toString())+"\n");
    msg.append("usedate: "+((usedate==null)?"null":usedate.toString())+"\n");
    return msg.toString();
  }

  /**	Required method.	*/
  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @Path("/description")
  public Response getInfo()
  {
    return Response.ok(getDescription(), MediaType.TEXT_PLAIN).build();
  }

  public void contextInitialized(ServletContextEvent servletContextEvent)
  {
    System.err.println("contextInitialized() called");
    initdate = new java.util.Date();
  }

  public void contextDestroyed(ServletContextEvent servletContextEvent)
  {
    System.err.println("contextDestroyed() called");
  }


  /////////////////////////////////////////////////////////////////////////////
  /**	main() only for validation. 
  */
  public static void main(String[] args)
	throws Exception
  {
    String warpath=(args.length>0)?args[0]:null;
    boolean ok=false;
    PluginValidator pv = new PluginValidator();
    try {
      if (warpath!=null)
      {
        System.out.println("WARPATH: "+warpath);
        ok=pv.validate(warpath);
      }
      else
        ok=pv.validate(helloinit_bardPlugin.class,null);
    } catch (InstantiationException e) {
      System.out.println("ERROR: (InstantiationException) "+e.getMessage());
    }
    System.out.println("validation status: "+(ok?"PASS":"FAIL"));
    for (String s: pv.getErrors()) System.out.println(s);
  }
}
