package edu.unm.health.biocomp.bard.hello;

import java.io.*;
import java.util.*;
import java.net.*; // MalformedURLException, URI, URISyntaxException, URL, URLEncoder
import java.sql.*;

import javax.servlet.*; // ServletConfig, ServletContext
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*; // GET, POST, Path, PathParam, QueryParam, FormParam, Produces, WebApplicationException
import javax.ws.rs.core.*; // Response, MediaType

import gov.nih.ncgc.bard.rest.BARDConstants;
import gov.nih.ncgc.bard.tools.*; // Util, PluginValidator
import gov.nih.ncgc.bard.plugin.*; // IPlugin, PluginManifest

/**	BARD plugin hello (world) from UNM.

	@author Jeremy Yang
*/
@Path("/hello")
public class hello_bardPlugin implements IPlugin
{
  private static final String PLUGIN_VERSION="0.9beta";
  private String HELLO_NOTE=null;		// configured via web.xml

  public hello_bardPlugin() {} // for validation only

  public hello_bardPlugin(
  	@Context ServletConfig servletConfig,
  	@Context ServletContext servletContext,
	@Context HttpServletRequest httpServletRequest,
	@Context HttpHeaders headers)
  {
    HELLO_NOTE=servletConfig.getInitParameter("HELLO_NOTE");
    if (HELLO_NOTE==null) HELLO_NOTE="none (hard-coded default)";
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
    pm.setAuthorEmail("jjyang@unm.edu");
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
        ok=pv.validate(hello_bardPlugin.class,null);
    } catch (InstantiationException e) {
      System.out.println("ERROR: (InstantiationException) "+e.getMessage());
    }
    System.out.println("validation status: "+(ok?"PASS":"FAIL"));
    for (String s: pv.getErrors()) System.out.println(s);
  }
}
