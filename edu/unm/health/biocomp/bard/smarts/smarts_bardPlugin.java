package edu.unm.health.biocomp.bard.smarts;

import java.io.*;
import java.util.*;
import java.util.regex.*; //Pattern
import java.net.*; // MalformedURLException, URI, URISyntaxException, URL, URLEncoder
import java.sql.*;

import javax.servlet.*; // ServletConfig, ServletContext
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*; // GET, POST, Path, PathParam, QueryParam, FormParam, Produces, WebApplicationException
import javax.ws.rs.core.*; // Response, MediaType

import org.json.*; // JSONObject
import org.xml.sax.SAXException;

import chemaxon.util.MolHandler;
import chemaxon.sss.search.MolSearch;
import chemaxon.struc.Molecule;
import chemaxon.formats.*; //MolImporter,MolFormatException
import chemaxon.sss.search.SearchException;

import gov.nih.ncgc.bard.rest.BARDConstants;
import gov.nih.ncgc.bard.tools.*; // Util, PluginValidator
import gov.nih.ncgc.bard.plugin.*; // IPlugin, PluginManifest
import gov.nih.ncgc.bard.plugin.PluginManifest.*; //PluginResource,PathArg

import edu.unm.health.biocomp.bard.util.*;  //bard_api_utils
import edu.unm.health.biocomp.smarts.*;  //


/**	Smarts molecular pattern matching, structural alerts, and filtering.
	<br>
	UNM BARD Team: Jeremy Yang, Anna Waller, Cristian Bologa, Oleg Ursu, Steve Mathias,
	Tudor Oprea, Larry Sklar.
	<br>
	Example URIs:
	<ul>
	<li> /smarts/unm_reactive/compound/54676228?expand=true&pretty=true
	<li> /smarts/unm_reactive/analyze?smiles=N%23CCO?expand=true
	<li> /smarts/alarmnmr/compound/2936384?expand=true
	</ul>
	<br>
	@author	Jeremy J Yang
*/
@Path("/smarts")
public class smarts_bardPlugin
	implements IPlugin, ServletContextListener
{
  private String NOTE=null;		// configured via web.xml (init-param)

  /**   static since belong to class/context, not object/servlet. */
  private static String PLUGIN_VERSION="1.0";
  private static PluginManifest PLUGIN_MANIFEST = null;
  private static String DESCRIPTION = null;
  private static String[] RESOURCE_PATHS = null;

  private static String BARD_API_HOST=null;		// configured via web.xml (context-param)
  private static String BARD_API_BASEPATH=null;		// configured via web.xml (context-param)
  private static String BARD_API_VERSION=null;		// configured via web.xml (context-param)
  private static String SMARTS_DIR=null;		// configured via web.xml (context-param)
  private static LinkedHashMap<String,SmartsFile> SMARTSFILES=null; //init in contextInitialized()

  public smarts_bardPlugin() {} // for validation only

  /**	Normal constructor used to read init params from web.xml.
  */
  public smarts_bardPlugin(
  	@Context ServletConfig servletConfig,
  	@Context ServletContext servletContext,
	@Context HttpServletRequest httpServletRequest,
	@Context HttpHeaders headers)
  {
    NOTE=servletConfig.getInitParameter("NOTE");
  }

  /**	Required method.	*/
  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @Path("/_version")
  public String getVersion() { return PLUGIN_VERSION; }

  /**	Required method.	*/
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/_manifest")
  public String getManifest()
  {
    if (PLUGIN_MANIFEST==null) this.initializeManifest(); //for validation only
    return PLUGIN_MANIFEST.toJson();
  }

  /**	Required method.	*/
  public String[] getResourcePaths()
  {
    if (RESOURCE_PATHS==null) return (new String[0]); //for validation only
    return RESOURCE_PATHS;
  }

  /**	Required method.	*/
  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @Path("/_info")
  public String getDescription()
  {
    if (DESCRIPTION==null) return (""); //for validation only
    String otxt=DESCRIPTION;
    String api_baseuri=bard_api_utils.BaseURI(BARD_API_HOST,BARD_API_BASEPATH,BARD_API_VERSION);
    boolean ok=false;
    ok=bard_api_utils.Ping(api_baseuri,0);
    otxt+=("BARD REST API connection ok: "+(ok?"YES":"NO"));
    return otxt;
  }

  /**	Required method.	*/
  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @Path("/description")
  public Response getInfo()
  {
    return Response.ok(getDescription(), MediaType.TEXT_PLAIN).build();
  }

  /**	Called by single and multiple compound requests.
	Return number of matches.
	@param jsonob return param
  */
  private static int CompoundSmartsFileAnalysis(Long cid,SmartsFile smartsFile,String expand,JSONObject jsonob)
  {
    String api_baseuri=bard_api_utils.BaseURI(BARD_API_HOST,BARD_API_BASEPATH,BARD_API_VERSION);
    String smi=null;
    try { smi=bard_api_utils.Cid2Smiles(api_baseuri,cid,0); } catch (Exception e) { }
    if (smi==null || smi.isEmpty())
      throw new WebApplicationException(new Exception("SMILES not found for CID: "+cid), 404);
    try { jsonob.put("cid",cid); }
    catch (JSONException e) { throw new WebApplicationException(e, 500); }
    return SmilesSmartsFileAnalysis(smi,smartsFile,expand,jsonob);
  }

  /**	Called by single and multiple compound requests.
	Return number of matches.
	@param jsonob return param
  */
  private static int SmilesSmartsFileAnalysis(String smi,SmartsFile smartsFile,String expand,JSONObject jsonob)
  {
    Molecule mol = null;
    try { mol=MolImporter.importMol(smi,"smiles:"); }
    catch (MolFormatException e) { throw new WebApplicationException(new Exception(e.toString()),500); }
    ArrayList<String> hitsmarts = new ArrayList<String>();
    for (int i=0;i<smartsFile.size();++i)
    {
      Smarts smarts=smartsFile.getSmarts(i);
      try {
        smarts.getSearch().setTarget(mol);
        if (smarts.getSearch().isMatching())
          hitsmarts.add(smarts.getRawsmarts()+" "+smarts.getName());
      }
      catch (Exception e) {
        continue;
      }
    }
    try {
      jsonob.put("smiles",smi);
      JSONObject jsonob2 = new JSONObject();
      jsonob2.put("pass",(hitsmarts.size()==0));
      jsonob2.put("set",smartsFile.getName().replaceFirst("\\.sma$",""));
      if (expand!=null)
      {
        jsonob2.put("n_matches",hitsmarts.size());
        jsonob2.put("n_patterns",smartsFile.size());
        jsonob2.put("matches",hitsmarts.toArray());
      }
      jsonob.put("results",jsonob2);
    } catch (JSONException e) {
      throw new WebApplicationException(e, 500);
    }
    return hitsmarts.size();
  }

  @GET
  @Produces({MediaType.APPLICATION_JSON,MediaType.TEXT_PLAIN})
  @Path("/{set}/compound/{cid}")
  public Response getSmartsMatches_JSON(
	@PathParam("set") String set,
	@PathParam("cid") Long cid,
	@QueryParam("expand") String expand,
	@QueryParam("pretty") String pretty)
  {
    SmartsFile smartsFile = SMARTSFILES.get(set+".sma");
    if (smartsFile==null)
      throw new WebApplicationException(new Exception("Illegal set param: "+set), 400);
    JSONObject jsonob = new JSONObject();
    CompoundSmartsFileAnalysis(cid,smartsFile,expand,jsonob);
    if (pretty!=null)
      try { return Response.ok(jsonob.toString(2),MediaType.APPLICATION_JSON).build(); } catch (Exception e) { }
    return Response.ok(jsonob.toString(), MediaType.APPLICATION_JSON).build();
  }

  @GET
  @Produces({MediaType.APPLICATION_JSON,MediaType.TEXT_PLAIN})
  @Path("/{set}/analyze")
  public Response getSmartsMatches_JSON(
	@PathParam("set") String set,
	@QueryParam("smiles") String smiles,
	@QueryParam("expand") String expand,
	@QueryParam("pretty") String pretty)
  {
    SmartsFile smartsFile = SMARTSFILES.get(set+".sma");
    if (smartsFile==null)
      throw new WebApplicationException(new Exception("Illegal set param: "+set), 400);
    if (smiles==null)
      throw new WebApplicationException(new Exception("SMILES required."), 400);
    JSONObject jsonob = new JSONObject();
    SmilesSmartsFileAnalysis(smiles,smartsFile,expand,jsonob);
    if (pretty!=null)
      try { return Response.ok(jsonob.toString(2),MediaType.APPLICATION_JSON).build(); } catch (Exception e) { }
    return Response.ok(jsonob.toString(), MediaType.APPLICATION_JSON).build();
  }

  /**	CIDs are comma separated.
  */
  @GET
  @Produces({MediaType.APPLICATION_JSON,MediaType.TEXT_PLAIN})
  @Path("/{set}/compounds/{cids}")
  public Response getSmartsMatches_JSON(
	@PathParam("set") String set,
	@PathParam("cids") String cids,
	@QueryParam("expand") String expand,
	@QueryParam("pretty") String pretty,
	@QueryParam("filter") String filter,
	@QueryParam("count") String count,
	@QueryParam("max_matches") Long max_matches)
  {
    SmartsFile smartsFile = SMARTSFILES.get(set+".sma");
    if (smartsFile==null)
      throw new WebApplicationException(new Exception("Illegal set param: "+set), 400);

    JSONArray jsonarray = new JSONArray();
    String[] cids_str = java.util.regex.Pattern.compile(",").split(cids);
    for (String cid_str: cids_str)
    {
      try
      {
        Long cid = Long.parseLong(cid_str);
        JSONObject jsonob = new JSONObject();
        CompoundSmartsFileAnalysis(cid,smartsFile,expand,jsonob);
        jsonarray.put(jsonob);
      }
      catch (Exception e)
      {
        System.out.println("DEBUG: "+e.getMessage());
      }
    }
    if (pretty!=null)
      try { return Response.ok(jsonarray.toString(2),MediaType.APPLICATION_JSON).build(); } catch (Exception e) { }
    return Response.ok(jsonarray.toString(), MediaType.APPLICATION_JSON).build();
  }

  /**	CIDs are in POST data.
  */
  @POST
  @Produces({MediaType.APPLICATION_JSON,MediaType.TEXT_PLAIN})
  @Path("/{set}/compounds")
  public Response getSmartsMatches_JSON_POST(
	@FormParam("set") String set,
	@FormParam("ids") String cids,
	@QueryParam("expand") String expand,
	@QueryParam("pretty") String pretty,
	@QueryParam("filter") String filter,
	@QueryParam("count") String count,
	@QueryParam("max_matches") Long max_matches)
  {
    if (cids == null)
      throw new WebApplicationException(new Exception("POST form ids parameter required, comma-separated string of CIDs"), 400);

    SmartsFile smartsFile = SMARTSFILES.get(set+".sma");
    if (smartsFile==null)
      throw new WebApplicationException(new Exception("Illegal set param: "+set), 400);

    String json_out=null;
    JSONArray jsonarray = new JSONArray();
    String[] cids_str = java.util.regex.Pattern.compile(",").split(cids);
    for (String cid_str: cids_str)
    {
      try
      {
        Long cid = Long.parseLong(cid_str);
        JSONObject jsonob = new JSONObject();
        CompoundSmartsFileAnalysis(cid,smartsFile,expand,jsonob);
        jsonarray.put(jsonob);
      }
      catch (Exception e)
      {
        System.out.println("DEBUG: "+e.getMessage());
      }
    }
    if (pretty!=null)
      try { return Response.ok(jsonarray.toString(2),MediaType.APPLICATION_JSON).build(); } catch (Exception e) { }
    return Response.ok(jsonarray.toString(), MediaType.APPLICATION_JSON).build();
  }

  /////////////////////////////////////////////////////////////////////////////
  private void initializeManifest()
  {
    PLUGIN_MANIFEST = new PluginManifest();
    PLUGIN_MANIFEST.setAuthor("Jeremy Yang");
    PLUGIN_MANIFEST.setAuthorEmail("jjyang@salud.unm.edu");
    PLUGIN_MANIFEST.setMaintainer(PLUGIN_MANIFEST.getAuthor());
    PLUGIN_MANIFEST.setMaintainerEmail(PLUGIN_MANIFEST.getAuthorEmail());
    PLUGIN_MANIFEST.setTitle("Smarts molecular pattern matching, structural alerts, and filtering");
    PLUGIN_MANIFEST.setDescription("Smarts molecular pattern matching, structural alerts, and filtering");
    PLUGIN_MANIFEST.setVersion(PLUGIN_VERSION);

    ArrayList<PluginResource> pluginResources = new ArrayList<PluginResource>();
    PluginResource res = new PluginResource();
    res.setPath("/compound/{cid}");
    res.setMimetype(MediaType.APPLICATION_JSON);
    res.setMethod("GET");
    res.setArgs(new PathArg[]{
	new PathArg("set", "string","path"),
	new PathArg("cid", "integer","path"),
	new PathArg("pretty", "boolean","query"),
	new PathArg("expand", "boolean","query")
	});
    pluginResources.add(res);
    res = new PluginResource();
    res.setPath("/analyze");
    res.setMimetype(MediaType.APPLICATION_JSON);
    res.setMethod("GET");
    res.setArgs(new PathArg[]{
	new PathArg("set", "string","path"),
	new PathArg("smiles", "string","query"),
	new PathArg("pretty", "boolean","query"),
	new PathArg("expand", "boolean","query")
	});
    pluginResources.add(res);
    res = new PluginResource();
    res.setPath("/substance/sid/{sid}");
    res.setMimetype(MediaType.APPLICATION_JSON);
    res.setMethod("GET");
    res.setArgs(new PluginManifest.PathArg[]{
	new PathArg("set", "string","path"),
	new PathArg("sid", "integer","path"),
	new PathArg("pretty", "boolean","query"),
	new PathArg("expand", "boolean","query")
	});
    pluginResources.add(res);
    res = new PluginResource();
    res.setPath("/compounds/{cids}");
    res.setMimetype(MediaType.APPLICATION_JSON);
    res.setMethod("GET");
    res.setArgs(new PluginManifest.PathArg[]{
	new PathArg("set", "string","path"),
	new PathArg("cids", "string","path"),
	new PathArg("pretty", "boolean","query"),
	new PathArg("expand", "boolean","query"),
	new PathArg("filter", "boolean","query"),
	new PathArg("max_matches", "integer","query"),
	new PathArg("count", "boolean","query")
	});
    pluginResources.add(res);
    res = new PluginResource();
    res.setPath("/compounds");
    res.setMimetype(MediaType.APPLICATION_JSON);
    res.setMethod("POST");
    res.setArgs(new PluginManifest.PathArg[]{
	new PathArg("set", "string","path"),
	new PathArg("ids", "string","form"),
	new PathArg("pretty", "boolean","query"),
	new PathArg("expand", "boolean","query"),
	new PathArg("filter", "boolean","query"),
	new PathArg("max_matches", "integer","query"),
	new PathArg("count", "boolean","query")
	});
    pluginResources.add(res);
    PLUGIN_MANIFEST.setResources(pluginResources.toArray(new PluginResource[0]));
  }

  /////////////////////////////////////////////////////////////////////////////
  protected void finalize() throws Throwable
  {
    super.finalize();
  }

  /////////////////////////////////////////////////////////////////////////////
  /**	Called once per webapp deployment.
	Init params must be context-scope (i.e. webapp-scope), not servlet-scope.
	Load Smarts here for efficiency.
  */
  public void contextInitialized(ServletContextEvent servletContextEvent)
  {
    ServletContext servletContext=servletContextEvent.getServletContext();
    if (servletContext==null)
    {
      servletContext.log("problem reading servletContext: "+servletContext);
      return;
    }
    for (Enumeration e=servletContext.getInitParameterNames(); e.hasMoreElements(); )
      servletContext.log("DEBUG: servletContext.getInitParameterNames() name: "+e.nextElement());

    BARD_API_HOST=servletContext.getInitParameter("BARD_API_HOST");
    BARD_API_BASEPATH=servletContext.getInitParameter("BARD_API_BASEPATH");
    BARD_API_VERSION=servletContext.getInitParameter("BARD_API_VERSION");

    SMARTS_DIR=servletContext.getInitParameter("SMARTS_DIR");
    if (SMARTS_DIR==null)
    {
      servletContext.log("problem reading SMARTS_DIR: "+SMARTS_DIR);
      return;
    }
    SMARTSFILES = new LinkedHashMap<String,SmartsFile>();
    File smarts_dir = new File(SMARTS_DIR);
    if (!smarts_dir.isDirectory())
    {
      servletContext.log("problem (!isDirectory) reading SMARTS_DIR: "+SMARTS_DIR);
      return;
    }
    for (String smafile: smarts_dir.list())
    {
      if (!smafile.matches("^.*\\.sma$")) {
        servletContext.log("ERROR: smarts file extension must be \".sma\".");
        continue;
      }
      SmartsFile smaf = new SmartsFile();
      try { smaf.parseFile(new File(SMARTS_DIR+"/"+smafile),false,smafile); }
      catch (Exception e) {
        servletContext.log("problem reading smarts file: "+e.toString());
        continue;
      }
      SMARTSFILES.put(smafile,smaf);
      servletContext.log("loaded smarts file: "+smafile+", name: "+smafile.replaceFirst("\\.sma$","")+" ("+smaf.size()+" smarts, "+smaf.getDefines().size()+" defs, "+smaf.getFailedsmarts().size()+" bad)");
    }

    /**	Create plugin manifest. */
    this.initializeManifest();

    /**	Create description string. */
    StringBuilder msg = new StringBuilder("Smarts molecular pattern matching, structural alerts, and filtering\n");
    msg.append("VERSION: "+getVersion()+" (May 2015)\n");
    msg.append("UNM BARD Team: Jeremy Yang, Anna Waller, Cristian Bologa, Oleg Ursu, Steve Mathias, Tudor Oprea, Larry Sklar\n");
    msg.append("NOTE: "+NOTE+"\n");
    msg.append("\nAvailable resources:\n");
    HashSet<String> paths_set = new HashSet<String>(); // for deduplication
    for (String path: Util.getResourcePaths(this.getClass())) paths_set.add(path);
    String[] paths = paths_set.toArray(new String[0]);
    Arrays.sort(paths);
    for (String path: paths) msg.append(path).append("\n");
    msg.append("smarts sets:\n");
    if (SMARTSFILES!=null)
    {
      for (String smaf_name: SMARTSFILES.keySet())
      {
        SmartsFile smaf=SMARTSFILES.get(smaf_name);
        msg.append("\t"+smaf_name.replaceFirst("\\.sma$","")+" ["+smaf.size()+"]\n");
      }
    }
    msg.append("BARD REST API: http://"+BARD_API_HOST+BARD_API_BASEPATH+"/"+BARD_API_VERSION+"\n");
    DESCRIPTION=msg.toString();

    /**	Create resource paths. */
    List<String> rpaths=Util.getResourcePaths(this.getClass());
    String[] RESOURCE_PATHS = new String[rpaths.size()];
    for (int i=0; i<rpaths.size(); ++i)
      RESOURCE_PATHS[i]=BARDConstants.API_BASE+rpaths.get(i);
  }

  /////////////////////////////////////////////////////////////////////////////
  /**	Called once per servlet/class un-deployment.
  */
  public void contextDestroyed(ServletContextEvent servletContextEvent)
  {
    ServletContext servletContext=servletContextEvent.getServletContext();
    servletContext.log("DEBUG: in contextDestroyed().");
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
        ok=pv.validate(smarts_bardPlugin.class,null);
    } catch (InstantiationException e) {
      System.out.println("ERROR: (InstantiationException) "+e.getMessage());
    }
    System.out.println("validation status: "+(ok?"PASS":"FAIL"));
    for (String s: pv.getErrors()) System.out.println(s);
  }
}
