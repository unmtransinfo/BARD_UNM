package edu.unm.health.biocomp.bard.ro5;

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
//import edu.unm.health.biocomp.vcclab.*; //vcclab_utils
import edu.unm.health.biocomp.cdk.*; //cdk_utils
import edu.unm.health.biocomp.ro5.*;  //


/**	Ro5 (Lipinski Rule of 5) calculations and filtering.
	<br>
	UNM BARD Team: Jeremy Yang, Anna Waller, Cristian Bologa, Oleg Ursu, Steve Mathias,
	Tudor Oprea, Larry Sklar.
	<br>
	Example URIs:
	<ul>
	<li> /ro5/compound/54676228
	<li> /ro5/compound/650361
	<li> /ro5/compound/2936384
	<li> /ro5/compounds/54676228,650361,2936384
	<li> /ro5/analyze?smiles=NCCc1cc(O)c(O)cc1
	</ul>
	@author	Jeremy J Yang
*/
@Path("/ro5")
public class ro5_bardPlugin implements IPlugin, ServletContextListener
{
  private String NOTE=null;		// configured via web.xml

  /**   static since belong to class/context, not object/servlet. */
  private static String PLUGIN_VERSION="1.0";
  private static PluginManifest PLUGIN_MANIFEST = null;
  private static String DESCRIPTION = null;
  private static String[] RESOURCE_PATHS = null;

  private static String BARD_API_HOST=null;          // configured via web.xml (context-param)
  private static String BARD_API_BASEPATH=null;              // configured via web.xml (context-param)
  private static String BARD_API_VERSION=null;               // configured via web.xml (context-param)

  public ro5_bardPlugin() {} // for validation only

  /**	Normal constructor used to read config params from web.xml.

  */
  public ro5_bardPlugin(
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
    return DESCRIPTION;
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
	Return number of Ro5 property violations [0-4].
	@param jsonob return param
  */
  private static Integer CompoundRo5(Long cid,String expand,JSONObject jsonob)
  {
    String api_baseuri = bard_api_utils.BaseURI(BARD_API_HOST,BARD_API_BASEPATH,BARD_API_VERSION);
    int n_violations = 0;
    try {
      JSONObject jsonob_in = bard_api_utils.Cid2Data(api_baseuri,cid,0);
      Ro5Result ro5_result = new Ro5Result();

      jsonob.put("cid",jsonob_in.isNull("cid")?"":jsonob_in.getLong("cid"));
      jsonob.put("smiles",jsonob_in.isNull("smiles")?"":jsonob_in.getString("smiles"));
      jsonob.put("MWT",jsonob_in.isNull("mwt")?0.0:String.format("%.2f",jsonob_in.getDouble("mwt")));
      jsonob.put("LOGP",jsonob_in.isNull("xlogp")?0.0:String.format("%.2f",jsonob_in.getDouble("xlogp")));
      jsonob.put("HBD",jsonob_in.isNull("hbondDonor")?0:jsonob_in.getInt("hbondDonor"));
      jsonob.put("HBA",jsonob_in.isNull("hbondAcceptor")?0:jsonob_in.getInt("hbondAcceptor"));

      ro5_result.setHbd(jsonob.getInt("HBD"));
      ro5_result.setHba(jsonob.getInt("HBA"));
      ro5_result.setLogp(new Float((jsonob.getDouble("LOGP"))));
      ro5_result.setMwt(jsonob.getDouble("MWT"));

      jsonob.put("ro5_violations",ro5_result.violations());
      n_violations = ro5_result.violations();

      if (expand!=null)
      {
        jsonob.put("LOGP_prog","xlogp");
        jsonob.put("compoundClass",jsonob_in.isNull("compoundClass")?"":jsonob_in.getString("compoundClass"));
        jsonob.put("probeId",jsonob_in.isNull("probeId")?"":jsonob_in.getString("probeId"));
        jsonob.put("ro5_violation_mwt",ro5_result.isViolationMwt());
        jsonob.put("ro5_violation_hbd",ro5_result.isViolationHbd());
        jsonob.put("ro5_violation_hba",ro5_result.isViolationHba());
        jsonob.put("ro5_violation_logp",ro5_result.isViolationLogp());
      }
    }
    catch (Exception e)
    {
      System.out.println("DEBUG: "+e.getMessage());
    }
    return (n_violations);
  }

  /**	Called by single smiles requests.
	Return number of Ro5 property violations [0-4].
	@param jsonob return param
  */
  private static Integer SmilesRo5(String smi,String expand,JSONObject jsonob)
  {
    int n_violations = 0;

    if (smi==null || smi.isEmpty())
      throw new WebApplicationException(new Exception("SMILES missing."), 500);

    Molecule mol = null;
    try { mol = MolImporter.importMol(smi,"smiles:"); }
    catch (MolFormatException e) { throw new WebApplicationException(new Exception("SMILES parse error: "+smi), 500); }

    MolHandler mhand = new MolHandler();
    Ro5Result ro5_result = new Ro5Result();
    ro5_result.setSmiles(smi);
    mol.hydrogenize(false);
    mhand.setMolecule(mol);
    ro5_result.setMwt(mhand.calcMolWeightInDouble());
    try { ro5_result.setHbd(HBonds.getDonors(mol)); }
    catch (Exception e) { }
    try { ro5_result.setHba(HBonds.getAcceptors(mol)); }
    catch (Exception e) { }

    //List<AlogpsResult> alogps_results=null;
    //String alogps_prog="?";
    //try { alogps_results = vcclab_utils.GetAlogpsResults(Arrays.asList(smi)); }
    //catch (Exception e) { }
    //AlogpsResult alogpsresult=alogps_results.get(0);
    //ro5_result.setLogp(alogpsresult.logp);

    Float xlogp = null;
    try { xlogp = cdk_utils.CalcXlogpFromSmiles(smi); }
    catch (Exception e) { throw new WebApplicationException(new Exception("Xlogp error: "+smi), 500); }
    ro5_result.setLogp(xlogp);

    try {
      jsonob.put("smiles",smi);
      jsonob.put("MWT",String.format("%.2f",ro5_result.getMwt()));
      jsonob.put("HBD",ro5_result.getHbd());
      jsonob.put("HBA",ro5_result.getHba());
      jsonob.put("LOGP",String.format("%.2f",ro5_result.getLogp()));
      jsonob.put("ro5_violations",ro5_result.violations());
      n_violations = ro5_result.violations();

      if (expand!=null)
      {
        //jsonob.put("LOGP_prog",alogpsresult.program);
        //if (alogpsresult.error!=null)
        //  jsonob.put("LOGP_error",alogpsresult.error);
        jsonob.put("ro5_violation_mwt",ro5_result.isViolationMwt());
        jsonob.put("ro5_violation_hbd",ro5_result.isViolationHbd());
        jsonob.put("ro5_violation_hba",ro5_result.isViolationHba());
        jsonob.put("ro5_violation_logp",ro5_result.isViolationLogp());
      }
    }
    catch (Exception e)
    {
      System.out.println("DEBUG: "+e.getMessage());
    }
    return (n_violations);
  }

  /**	JSON Response returned.
  */
  @GET
  @Produces({MediaType.APPLICATION_JSON,MediaType.TEXT_PLAIN})
  @Path("/compound/{cid}")
  public Response getRo5_JSON(
	@PathParam("cid") Long cid,
	@QueryParam("expand") String expand,
	@QueryParam("pretty") String pretty)
  {
    JSONObject jsonob = new JSONObject();
    CompoundRo5(cid,expand,jsonob);
    if (pretty!=null)
      try { return Response.ok(jsonob.toString(2),MediaType.APPLICATION_JSON).build(); } catch (Exception e) { }
    return Response.ok(jsonob.toString(), MediaType.APPLICATION_JSON).build();
  }

  /**	JSON Response returned.
  */
  @GET
  @Produces({MediaType.APPLICATION_JSON,MediaType.TEXT_PLAIN})
  @Path("/analyze")
  public Response getRo5_JSON(
	@QueryParam("smiles") String smiles,
	@QueryParam("expand") String expand,
	@QueryParam("pretty") String pretty)
  {
    if (smiles==null)
      throw new WebApplicationException(new Exception("SMILES required."), 400); //bad request
    JSONObject jsonob = new JSONObject();
    SmilesRo5(smiles,expand,jsonob);
    if (pretty!=null)
      try { return Response.ok(jsonob.toString(2),MediaType.APPLICATION_JSON).build(); } catch (Exception e) { }
    return Response.ok(jsonob.toString(), MediaType.APPLICATION_JSON).build();
  }

  /**	CIDs are comma separated.
  */
  @GET
  @Produces({MediaType.APPLICATION_JSON,MediaType.TEXT_PLAIN})
  @Path("/compounds/{cids}")
  public Response getRo5_JSON(
	@PathParam("cids") String cids,
	@QueryParam("expand") String expand,
	@QueryParam("filter") String filter,
	@QueryParam("max_violations") Long max_violations,
	@QueryParam("count") String count,
	@QueryParam("pretty") String pretty)
  {
    JSONArray jsonarray = new JSONArray();
    if (max_violations==null) max_violations=1L;
    String[] cids_str = java.util.regex.Pattern.compile(",").split(cids);
    for (String cid_str: cids_str)
    {
      try
      {
        Long cid = Long.parseLong(cid_str);
        JSONObject jsonob = new JSONObject();
        int n_violations = CompoundRo5(cid,expand,jsonob);
        boolean pass = (n_violations<=max_violations);
        if (pass || filter==null) jsonarray.put(jsonob);
      }
      catch (Exception e)
      {
        System.out.println("DEBUG: "+e.getMessage());
      }
    }
    if (count!=null)
      return Response.ok(""+jsonarray.length(), MediaType.TEXT_PLAIN).build();

    if (pretty!=null)
      try { return Response.ok(jsonarray.toString(2),MediaType.APPLICATION_JSON).build(); } catch (Exception e) { }
    return Response.ok(jsonarray.toString(), MediaType.APPLICATION_JSON).build();
  }

  /**	CIDs are in POST data.
  */
  @POST
  @Produces({MediaType.APPLICATION_JSON,MediaType.TEXT_PLAIN})
  @Path("/compounds")
  public Response getRo5_JSON_POST(
	@FormParam("ids") String cids,
	@QueryParam("expand") String expand,
	@QueryParam("filter") String filter,
	@QueryParam("max_violations") Long max_violations,
	@QueryParam("count") String count,
	@QueryParam("pretty") String pretty)
  {
    if (cids == null)
      throw new WebApplicationException(new Exception("POST form ids parameter required, comma-separated string of CIDs"), 400);

    JSONArray jsonarray = new JSONArray();
    String[] cids_str = java.util.regex.Pattern.compile(",").split(cids);
    for (String cid_str: cids_str)
    {
      try
      {
        Long cid = Long.parseLong(cid_str);
        JSONObject jsonob = new JSONObject();
        int n_violations = CompoundRo5(cid,expand,jsonob);
        boolean pass = (n_violations<=max_violations);
        if (pass || filter==null) jsonarray.put(jsonob);
        jsonarray.put(jsonob);
      }
      catch (Exception e)
      {
        System.out.println("DEBUG: "+e.getMessage());
      }
    }
    if (count!=null)
      return Response.ok(""+jsonarray.length(), MediaType.TEXT_PLAIN).build();

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
    PLUGIN_MANIFEST.setTitle("Ro5 (Lipinski Rule of 5) calculations and filtering");
    PLUGIN_MANIFEST.setDescription("Ro5 (Lipinski Rule of 5) calculations and filtering");
    PLUGIN_MANIFEST.setVersion(PLUGIN_VERSION);
    ArrayList<PluginResource> pluginResources = new ArrayList<PluginResource>();
    PluginResource res = null;
    res = new PluginResource();
    res.setPath("/compound/{cid}");
    res.setMimetype(MediaType.APPLICATION_JSON);
    res.setMethod("GET");
    res.setArgs(new PathArg[]{
	new PathArg("cid", "integer","path"),
	new PathArg("expand", "boolean","query")
	});
    pluginResources.add(res);
    res = new PluginResource();
    res.setPath("/analyze");
    res.setMimetype(MediaType.APPLICATION_JSON);
    res.setMethod("GET");
    res.setArgs(new PathArg[]{
	new PathArg("smiles", "string","query"),
	new PathArg("expand", "boolean","query"),
	new PathArg("pretty", "boolean","query")
	});
    pluginResources.add(res);

//    res = new PluginResource();
//    res.setPath("/substance/sid/{sid}");
//    res.setMimetype(MediaType.APPLICATION_JSON);
//    res.setMethod("GET");
//    res.setArgs(new PathArg[]{
//	new PathArg("sid", "integer","path"),
//	new PathArg("expand", "boolean","query"),
//	new PathArg("pretty", "boolean","query")
//	});
//    pluginResources.add(res);

    res = new PluginResource();
    res.setPath("/compounds/{cids}");
    res.setMimetype(MediaType.APPLICATION_JSON);
    res.setMethod("GET");
    res.setArgs(new PathArg[]{
	new PathArg("cids", "string","path"),
	new PathArg("expand", "boolean","query"),
	new PathArg("filter", "boolean","query"),
	new PathArg("max_violations", "integer","query"),
	new PathArg("count", "boolean","query"),
	new PathArg("pretty", "boolean","query")
	});
    pluginResources.add(res);
    res = new PluginResource();
    res.setPath("/compounds");
    res.setMimetype(MediaType.APPLICATION_JSON);
    res.setMethod("POST");
    res.setArgs(new PathArg[]{
	new PathArg("ids", "string","form"),
	new PathArg("expand", "boolean","query"),
	new PathArg("filter", "boolean","query"),
	new PathArg("max_violations", "integer","query"),
	new PathArg("count", "boolean","query"),
	new PathArg("pretty", "boolean","query")
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
  /**   Called once per webapp deployment, after constructor.
        Here we initiate db connection[s].
        Init params must be context-scope (i.e. webapp-scope), not servlet-scope.
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
    {
      String param = (String)e.nextElement();
      servletContext.log("DEBUG: servletContext param: "+param+": "+servletContext.getInitParameter(param));
    }
    BARD_API_HOST=servletContext.getInitParameter("BARD_API_HOST");
    BARD_API_BASEPATH=servletContext.getInitParameter("BARD_API_BASEPATH");
    BARD_API_VERSION=servletContext.getInitParameter("BARD_API_VERSION");

    /**	Create plugin manifest. */
    this.initializeManifest();

    /**	Create description string. */
    StringBuilder msg = new StringBuilder("Ro5 (Lipinski Rule of 5) calculations and filtering\n");
    msg.append("VERSION: "+getVersion()+" (May 2015)\n");
    msg.append("UNM BARD Team: Jeremy Yang, Anna Waller, Cristian Bologa, Oleg Ursu, Steve Mathias, Tudor Oprea, Larry Sklar\n");
    msg.append("NOTE: "+NOTE+"\n");
    msg.append("Uses API: http://"+BARD_API_HOST+BARD_API_BASEPATH+"/"+BARD_API_VERSION+"\n");
    msg.append("\nAvailable resources:\n");
    HashSet<String> paths_set = new HashSet<String>(); // for deduplication
    for (String path: Util.getResourcePaths(this.getClass())) paths_set.add(path);
    String[] paths = paths_set.toArray(new String[0]);
    Arrays.sort(paths);
    for (String path: paths) msg.append(path).append("\n");
    DESCRIPTION=msg.toString();

    /**	Create resource paths. */
    List<String> rpaths=Util.getResourcePaths(this.getClass());
    String[] RESOURCE_PATHS = new String[rpaths.size()];
    for (int i=0; i<rpaths.size(); ++i)
      RESOURCE_PATHS[i]=BARDConstants.API_BASE+rpaths.get(i);
  }

  /////////////////////////////////////////////////////////////////////////////
  /**   Called once per servlet/class un-deployment.
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
        ok=pv.validate(ro5_bardPlugin.class,null);
    } catch (InstantiationException e) {
      System.out.println("ERROR: (InstantiationException) "+e.getMessage());
    }
    System.out.println("validation status: "+(ok?"PASS":"FAIL"));
    for (String s: pv.getErrors()) System.out.println(s);
  }
}
