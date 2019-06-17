package edu.unm.health.biocomp.bard.badapple;

import java.io.*;
import java.util.*;
import java.net.*; //MalformedURLException,URI,URISyntaxException,URL,URLEncoder
import java.sql.*;

import javax.servlet.*; //ServletConfig,ServletContext
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*; //GET,POST,Path,PathParam,QueryParam,FormParam,Produces,WebApplicationException
import javax.ws.rs.core.*; //Response,MediaType

import org.json.*; //JSONObject
import org.xml.sax.SAXException;

import chemaxon.formats.*;
import chemaxon.struc.*;
import chemaxon.marvin.util.*;
import chemaxon.marvin.io.*; //MolExportException
import chemaxon.util.MolHandler;
import chemaxon.sss.search.SearchException;

import gov.nih.ncgc.bard.rest.BARDConstants;
import gov.nih.ncgc.bard.tools.*; //Util,PluginValidator
import gov.nih.ncgc.bard.plugin.*; //IPlugin,PluginManifest
import gov.nih.ncgc.bard.plugin.PluginManifest.*; //PluginResource,PathArg

import edu.unm.health.biocomp.util.db.*; //DBCon
import edu.unm.health.biocomp.badapple.*;  //badapple_utils

/**	BADAPPLE Promiscuity Plugin from UNM, evidence-based promiscuity scores.
	<br>
	UNM BARD Team: Jeremy Yang, Anna Waller, Cristian Bologa, Oleg Ursu, Steve Mathias,
	Tudor Oprea, Larry Sklar.
	<br>
	See Badapple plugin API and other docs at BARD Docs.
	<br>
	Example URIs:
	<ul>
	<li> /badapple/_info
	<li> /badapple/prom/cid/644397
	<li> /badapple/prom/scafid/67
	<li> /badapple/prom/analyze?smiles=COc1cc2c(ccnc2cc1)C(O)C4CC(CC3)C(C=C)CN34
	</ul>
	@author	Jeremy J Yang
*/
@Path("/badapple")
public class badapple_bardPlugin implements IPlugin, ServletContextListener
{
  private String NOTE=null;		// configured via web.xml (init-param)

  /**	static since belong to class/context, not object/servlet. */
  private static String PLUGIN_VERSION="1.02";
  private static PluginManifest PLUGIN_MANIFEST = null;
  private static String DESCRIPTION = null;
  private static String[] RESOURCE_PATHS = null;

  private static String BADAPPLE_DBTYPE="";		// configured via web.xml (context-param)
  private static String BADAPPLE_DBHOST=null;		// configured via web.xml (context-param)
  private static Integer BADAPPLE_DBPORT=null;		// configured via web.xml (context-param)
  private static String BADAPPLE_DBNAME=null;		// configured via web.xml (context-param)
  private static String BADAPPLE_DBSCHEMA=null;	// configured via web.xml (context-param)
  private static String BADAPPLE_DBUSR=null;		// configured via web.xml (context-param)
  private static String BADAPPLE_DBPW=null;		// configured via web.xml (context-param)

  /**	Persistent connection, belongs to class/context, not object/servlet. */
  private static DBCon DBCON = null;

  public badapple_bardPlugin() {} // for validation only

  /**	Normal constructor used to read config params from web.xml.

	Params read:
	<ul>
	<li>NOTE
	<li>BADAPPLE_DBTYPE
	<li>BADAPPLE_DBHOST
	<li>BADAPPLE_DBPORT
	<li>BADAPPLE_DBNAME
	<li>BADAPPLE_DBSCHEMA
	<li>BADAPPLE_DBUSR
	<li>BADAPPLE_DBPW
	</ul>
	Since Sept. 2013 beta version, the database is expected to be Derby (local, embedded),
	but PostgreSQL continues to be supported (alpha version expected PostgreSQL).
  */
  public badapple_bardPlugin(
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

  /**	This method queries the database containing BADAPPLE data
	for the scaffold ID specified.  A ScaffoldScore is returned
	by badapple_utils.GetScaffoldScore().
	JSON Response returned.
  */
  @GET
  @Produces({MediaType.APPLICATION_JSON,MediaType.TEXT_PLAIN})
  @Path("/prom/scafid/{scafid}")
  public Response getPromiscuityScoresScaf_JSON(
	@PathParam("scafid") Long scafid,
	@QueryParam("expand") String expand,
	@QueryParam("pretty") String pretty,
	@QueryParam("debug") String debug)
  {
    ScaffoldScore score=null;
    try { score=badapple_utils.GetScaffoldScore(DBCON,BADAPPLE_DBSCHEMA,null,scafid,0); } //verbose=0
    catch (Exception e) { throw new WebApplicationException(e, 500); }
    if (score==null) throw new WebApplicationException(404); // not found

    JSONObject jsonob = new JSONObject();
    try {
      jsonob.put("scafid",scafid);
      jsonob.put("scafsmi",score.getSmiles());
      jsonob.put("pScore",score.getScore());
      jsonob.put("sTested",score.getSubTested());
      jsonob.put("sActive",score.getSubActive());
      jsonob.put("aTested",score.getAsyTested());
      jsonob.put("aActive",score.getAsyActive());
      jsonob.put("wTested",score.getSamTested());
      jsonob.put("wActive",score.getSamActive());
      if (expand!=null) 
        jsonob.put("inDrug",score.getInDrug());
    } catch (JSONException e) {
      throw new WebApplicationException(e, 500);
    }
    if (pretty!=null)
      try { return Response.ok(jsonob.toString(2),MediaType.APPLICATION_JSON).build(); } catch (Exception e) { }
    return Response.ok(jsonob.toString(), MediaType.APPLICATION_JSON).build();
  }

  /**	This method queries the database containing BADAPPLE data
	for the compound ID specified.  ScaffoldScores are returned
	by badapple_utils.GetScaffoldScoresForDBMol().
	JSON Response returned.
  */
  @GET
  @Produces({MediaType.APPLICATION_JSON,MediaType.TEXT_PLAIN})
  @Path("/prom/cid/{cid}")
  public Response getPromiscuityScoresCpd_JSON(
	@PathParam("cid") Long cid,
	@QueryParam("expand") String expand,
	@QueryParam("pretty") String pretty,
	@QueryParam("debug") String debug)
  {
    ArrayList<ScaffoldScore> scores=null;
    try { scores=badapple_utils.GetScaffoldScoresForDBMol(DBCON,BADAPPLE_DBSCHEMA,null,cid,0); }
    catch (Exception e) { throw new WebApplicationException(e, 500); } //maybe not-found scaf
    if (scores==null) throw new WebApplicationException(404); // not found

    Collections.sort(scores); //descending score order

    JSONObject jsonob = new JSONObject();
    try {
      jsonob.put("cid",cid);

      for (ScaffoldScore score: scores)
      {
        if (score==null) continue;  //why?
        JSONObject jsonob2 = new JSONObject();
        jsonob2.put("scafid",score.getID());
        jsonob2.put("smiles",score.getSmiles());
        jsonob2.put("pScore",score.getScore());
        if (expand!=null) 
        {
          jsonob2.put("sTested",score.getSubTested());
          jsonob2.put("sActive",score.getSubActive());
          jsonob2.put("aTested",score.getAsyTested());
          jsonob2.put("aActive",score.getAsyActive());
          jsonob2.put("wTested",score.getSamTested());
          jsonob2.put("wActive",score.getSamActive());
          jsonob2.put("inDrug",score.getInDrug());
        }
        jsonob.append("hscafs",jsonob2);
      }
    } catch (JSONException e) {
      throw new WebApplicationException(e, 500);
    }
    scores.clear(); //free-memory?
    if (pretty!=null)
      try { return Response.ok(jsonob.toString(2),MediaType.APPLICATION_JSON).build(); } catch (Exception e) { }
    return Response.ok(jsonob.toString(), MediaType.APPLICATION_JSON).build();
  }

  /**	This method analyzes an input query molecule.  Currently smiles parameter
	is required and only input format.
	The database is searched for all scaffolds in the query and
	ScaffoldScores are returned by badapple_utils.GetScaffoldScores().
  */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/prom/analyze")
  public Response getPromiscuityScoresQuerymol_JSON(
	@QueryParam("smiles") String smiles,
	@QueryParam("expand") String expand,
	@QueryParam("pretty") String pretty,
	@QueryParam("debug") String debug)
  {
    if (smiles==null) throw new WebApplicationException(400); // bad request
    Molecule mol=null;
    ArrayList<ScaffoldScore> scores=null;
    try {
      mol=MolImporter.importMol(smiles,"smiles:");
    }
    catch (Exception e) { throw new WebApplicationException(e, 500); } //maybe no smiles
    if (mol==null) throw new WebApplicationException(500); // maybe bad smiles
    try {
      scores=badapple_utils.GetScaffoldScores(DBCON,BADAPPLE_DBSCHEMA,null,mol,0);
    }
    catch (Exception e) { throw new WebApplicationException(e, 500); } //maybe not-found scaf

    if (scores==null) throw new WebApplicationException(404); // not found

    Collections.sort(scores); //descending score order

    JSONObject jsonob = new JSONObject();
    try {
      int highscore=0;
      jsonob.put("query",smiles);
      for (ScaffoldScore score: scores)
      {
        if (score==null) continue;  //why?
        JSONObject jsonob2 = new JSONObject();
        jsonob2.put("scafid",score.getID());
        jsonob2.put("smiles",score.getSmiles());
        jsonob2.put("pScore",score.getScore());
        highscore = Math.max(highscore,(int)Math.floor(score.getScore()));
        if (expand!=null) 
        {
          jsonob2.put("sTested",score.getSubTested());
          jsonob2.put("sActive",score.getSubActive());
          jsonob2.put("aTested",score.getAsyTested());
          jsonob2.put("aActive",score.getAsyActive());
          jsonob2.put("wTested",score.getSamTested());
          jsonob2.put("wActive",score.getSamActive());
          jsonob2.put("inDrug",score.getInDrug());
        }
        jsonob.append("hscafs",jsonob2);
      }
      jsonob.put("highscore",highscore);
    } catch (JSONException e) {
      throw new WebApplicationException(e, 500);
    }
    scores.clear(); //free-memory?
    if (pretty!=null)
      try { return Response.ok(jsonob.toString(2),MediaType.APPLICATION_JSON).build(); } catch (Exception e) { }
    return Response.ok(jsonob.toString(),MediaType.APPLICATION_JSON).build();
  }

  /////////////////////////////////////////////////////////////////////////////
  private void initializeManifest()
  {
    PLUGIN_MANIFEST = new PluginManifest();
    PLUGIN_MANIFEST.setAuthor("Jeremy Yang");
    PLUGIN_MANIFEST.setAuthorEmail("jjyang@salud.unm.edu");
    PLUGIN_MANIFEST.setMaintainer(PLUGIN_MANIFEST.getAuthor());
    PLUGIN_MANIFEST.setMaintainerEmail(PLUGIN_MANIFEST.getAuthorEmail());
    PLUGIN_MANIFEST.setTitle("BADAPPLE evidence-based promiscuity scores");
    PLUGIN_MANIFEST.setDescription(
	"BADAPPLE is a bioassay data analysis algorithm, robust to noise and errors, "
	+"skeptical of scanty evidence.  BADAPPLE = Bioactivity Data Associative Promiscuity "
	+"Pattern Learning Engine."
	);
    PLUGIN_MANIFEST.setVersion(PLUGIN_VERSION);

    ArrayList<PluginResource> pluginResources = new ArrayList<PluginResource>();

    PluginResource res = new PluginResource();
    res.setPath("/prom/scafid/{scafid}");
    res.setMimetype(MediaType.APPLICATION_JSON);
    res.setMethod("GET");
    res.setArgs(new PathArg[]{
	new PathArg("scafid", "integer","path"),
	new PathArg("expand", "boolean","query"),
	new PathArg("pretty", "boolean","query")
	});
    pluginResources.add(res);

    res = new PluginResource();
    res.setPath("/prom/cid/{cid}");
    res.setMimetype(MediaType.APPLICATION_JSON);
    res.setMethod("GET");
    res.setArgs(new PathArg[]{
	new PathArg("cid", "integer","path"),
	new PathArg("expand", "boolean","query"),
	new PathArg("pretty", "boolean","query")
	});
    pluginResources.add(res);

    res = new PluginResource();
    res.setPath("/prom/analyze");
    res.setMimetype(MediaType.APPLICATION_JSON);
    res.setMethod("GET");
    res.setArgs(new PathArg[]{
	new PathArg("smiles", "string","query"),
	new PathArg("expand", "boolean","query"),
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
    servletContext.log("DEBUG: in contextInitialized().");
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
    BADAPPLE_DBTYPE=servletContext.getInitParameter("BADAPPLE_DBTYPE");
    if (BADAPPLE_DBTYPE==null)
    {
      servletContext.log("problem reading BADAPPLE_DBTYPE: "+BADAPPLE_DBTYPE);
      return;
    }
    BADAPPLE_DBHOST=servletContext.getInitParameter("BADAPPLE_DBHOST");
    if (BADAPPLE_DBHOST==null) BADAPPLE_DBHOST="localhost";
    try { BADAPPLE_DBPORT=Integer.parseInt(servletContext.getInitParameter("BADAPPLE_DBPORT")); }
    catch (Exception e) { BADAPPLE_DBPORT=5432; } //NA for Derby
    BADAPPLE_DBNAME=servletContext.getInitParameter("BADAPPLE_DBNAME");
    if (BADAPPLE_DBNAME==null) BADAPPLE_DBNAME="/usr/local/tomcat/webapps/bardplugin_badapple/derby/badapple";
    BADAPPLE_DBSCHEMA=servletContext.getInitParameter("BADAPPLE_DBSCHEMA");
    if (BADAPPLE_DBSCHEMA==null) BADAPPLE_DBSCHEMA="APP"; //Derby default
    BADAPPLE_DBUSR=servletContext.getInitParameter("BADAPPLE_DBUSR");
    if (BADAPPLE_DBUSR==null) BADAPPLE_DBUSR="bard"; //NA for Derby
    BADAPPLE_DBPW=servletContext.getInitParameter("BADAPPLE_DBPW");
    if (BADAPPLE_DBPW==null) BADAPPLE_DBPW="stratford"; //NA for Derby
    try {
      DBCON = new DBCon(BADAPPLE_DBTYPE,BADAPPLE_DBHOST,BADAPPLE_DBPORT,BADAPPLE_DBNAME,BADAPPLE_DBUSR,BADAPPLE_DBPW);
      servletContext.log("DEBUG: connection ok: "+BADAPPLE_DBTYPE);
    }
    catch (Exception e) {
      servletContext.log("ERROR: connection failed: "+BADAPPLE_DBTYPE);
    }

    /**	Create plugin manifest.	*/
    this.initializeManifest();


    /**	Create description string. */
    StringBuilder sb = new StringBuilder("BADAPPLE Promiscuity Plugin from UNM, evidence-based promiscuity scores\n");
    sb.append("VERSION: "+getVersion()+" (May 2015)\n");
    sb.append("UNM BARD Team: Jeremy Yang, Anna Waller, Cristian Bologa, Oleg Ursu, Steve Mathias, Tudor Oprea, Larry Sklar\n");
    sb.append("database ("+BADAPPLE_DBTYPE+"): "+BADAPPLE_DBHOST+":"
	+(BADAPPLE_DBTYPE.equalsIgnoreCase("derby")?"":BADAPPLE_DBPORT+":")
	+BADAPPLE_DBNAME
	+(BADAPPLE_DBTYPE.equalsIgnoreCase("postgres")?(":"+BADAPPLE_DBSCHEMA):"")
	+"\n");
    sb.append("NOTE: "+NOTE+"\n");
    if (DBCON==null) sb.append("DEBUG: DBCON==null.  Aaack!\n");
    sb.append("\nAvailable resources:\n");
    HashSet<String> paths_set = new HashSet<String>(); // for deduplication
    for (String path: Util.getResourcePaths(this.getClass())) paths_set.add(path);
    String[] paths = paths_set.toArray(new String[0]);
    Arrays.sort(paths);
    for (String path: paths) sb.append(path).append("\n");
    try { sb.append("range(scafid): [1,"+badapple_utils.GetMaxScafID(DBCON,BADAPPLE_DBSCHEMA)+"]"); }
    catch (Exception e) { sb.append("ERROR: "+e.getMessage()); }
    DESCRIPTION = sb.toString();

    /**	Create resource paths. */
    List<String> rpaths=Util.getResourcePaths(this.getClass());
    RESOURCE_PATHS = new String[rpaths.size()];
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
    try { DBCON.close(); }
    catch (Exception e) { System.err.println("ERROR: problem closing connection: "+e.getMessage()); }
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
        ok=pv.validate(badapple_bardPlugin.class,null);
    } catch (InstantiationException e) {
      System.out.println("ERROR: (InstantiationException) "+e.getMessage());
    }
    System.out.println("validation status: "+(ok?"PASS":"FAIL"));
    for (String s: pv.getErrors()) System.out.println(s);
  }
}
