package edu.unm.health.biocomp.bard.vcclab;

import java.io.*;
import java.util.*;
import java.net.*; // MalformedURLException, URI, URISyntaxException, URL, URLEncoder
import java.sql.*;

import javax.servlet.*; // ServletConfig, ServletContext
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*; // GET, POST, Path, PathParam, QueryParam, FormParam, Produces, WebApplicationException
import javax.ws.rs.core.*; // Context, Response, MediaType

import org.json.*; // JSONObject
import org.xml.sax.SAXException;

import gov.nih.ncgc.bard.rest.BARDConstants;
import gov.nih.ncgc.bard.tools.*; // Util, PluginValidator
import gov.nih.ncgc.bard.plugin.*; // IPlugin, PluginManifest
import gov.nih.ncgc.bard.plugin.PluginManifest.*; // PluginResource,PathArg

import edu.unm.health.biocomp.vcclab.*;

/**	VCCLAB BARD Plugin, from UNM; data link toVCCLAB web
	services via http://www.vcclab.org/.

	<br/>
	Example URIs:
	<UL>
	  <LI>/vcclab/alogps?smiles=CC1=C(C(=NO1)C)C2=CC3=C(C=C2)N=CN=C3NCCC4=CNC5=C4C=C(C=C5)OC
	</UL>
	<br/>
	@author	Jeremy Yang
	@see	gov.nih.ncgc.bard.plugin.IPlugin
*/
@Path("/vcclab")
public class vcclab_bardPlugin implements IPlugin
{
  private static String PLUGIN_VERSION="0.9beta";
  private static String NOTE=null;	//configured in web.xml

  public vcclab_bardPlugin() {} 	//for validation only

  /**	Normal constructor used to read config params from web.xml.
  */
  public vcclab_bardPlugin(
	@Context ServletConfig servletConfig,
	@Context ServletContext servletContext,
	@Context HttpServletRequest httpServletRequest,
	@Context HttpHeaders headers)
  {
    NOTE=servletConfig.getInitParameter("NOTE");
    if (NOTE==null) NOTE="none (hard-coded default)";
  }

  /**	Required method.	*/
  @GET
  @Path("/_version")
  @Produces(MediaType.TEXT_PLAIN)
  public String getVersion() { return PLUGIN_VERSION; }

  /**	Required method.	*/
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/_manifest")
  public String getManifest()
  {
    PluginManifest pm = new PluginManifest();
    pm.setAuthor("Jeremy Yang");
    pm.setAuthorEmail("jjyang@salud.unm.edu");
    pm.setMaintainer(pm.getAuthor());
    pm.setMaintainerEmail(pm.getAuthorEmail());
    pm.setTitle("VCCLAB/ALOGPS demo");
    pm.setDescription("ALOGPS calculator.");
    pm.setVersion(PLUGIN_VERSION);

    ArrayList<PluginResource> pluginResources = new ArrayList<PluginResource>();
    PluginResource res = new PluginResource();
    res.setPath("/alogps");
    res.setMimetype(MediaType.APPLICATION_JSON);
    res.setMethod("GET");
    res.setArgs(new PathArg[]{
	new PathArg("smiles", "string","query"),
	new PathArg("expand", "boolean","query"),
	new PathArg("pretty", "boolean","query"),
	new PathArg("raw", "boolean","query")
	});
    pluginResources.add(res);

    pm.setResources(pluginResources.toArray(new PluginResource[0]));

    return pm.toJson();
  }

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
  @Path("/_info")
  @Produces(MediaType.TEXT_PLAIN)
  public String getDescription()
  {
    StringBuilder msg = new StringBuilder("VCCLAB BARD Plugin, from UNM; datalink to VCCLAB.ORG services.\n");
    msg.append("note: "+NOTE+"\n");
    msg.append("\nAvailable resources:\n");
    HashSet<String> paths_set = new HashSet<String>(); // for deduplication
    for (String path: Util.getResourcePaths(this.getClass())) paths_set.add(path);
    Object [] paths = paths_set.toArray();
    Arrays.sort(paths);
    for (Object path: paths) msg.append(""+path).append("\n");
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

  /**	This method processes the query molecule specified by smiles, returns the Alogps result.
	<br>
	JSON Response returned.
	<br>
  */
  @GET
  @Path("/alogps")
  @Produces(MediaType.APPLICATION_JSON)
  public Response smiAlogps_JSON(
	@QueryParam("smiles") String smiles,
	@QueryParam("expand") String expand,
	@QueryParam("raw") String raw,
	@QueryParam("pretty") String pretty)
  {
    if (smiles==null) throw new WebApplicationException(400); //bad request

    List<String> smis = new ArrayList<String>();
    smis.add(smiles);

    org.json.JSONObject jsonob = new JSONObject();
    try
    {
      List<AlogpsResult> results = vcclab_utils.GetAlogpsResults(smis);
      if (results.size()==0) //error
      {
        throw new WebApplicationException(404); // not found
      }
      jsonob.put("smiles",results.get(0).smi);
      jsonob.put("logp",results.get(0).logp);
      jsonob.put("logperr",results.get(0).logperr);
      jsonob.put("logs",results.get(0).logs);
      jsonob.put("logserr",results.get(0).logserr);
      jsonob.put("sol",results.get(0).sol);
      jsonob.put("sol_units",results.get(0).sol_units);
      jsonob.put("input",results.get(0).input);
      jsonob.put("program",results.get(0).program);
      if (expand!=null && results.get(0).warning!=null)
        jsonob.put("warning",results.get(0).warning);
      if (results.get(0).error!=null)
        jsonob.put("error",results.get(0).error);
    }
    catch (JSONException e) {
      throw new WebApplicationException(e, 500);
    }
    catch (Exception e) {
      throw new WebApplicationException(e, 500);
    }
    if (pretty!=null)
      try { return Response.ok(jsonob.toString(2),MediaType.APPLICATION_JSON).build(); } catch (Exception e) { }
    return Response.ok(jsonob.toString(), MediaType.APPLICATION_JSON).build();
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
        ok=pv.validate(vcclab_bardPlugin.class,null);
    } catch (InstantiationException e) {
      System.out.println("ERROR: (InstantiationException) "+e.getMessage());
    }
    System.out.println("validation status: "+(ok?"PASS":"FAIL"));
    for (String s: pv.getErrors()) System.out.println(s);
  }
}
