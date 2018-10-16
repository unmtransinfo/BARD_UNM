package edu.unm.health.biocomp.bard.hscaf;

import java.io.*;
import java.util.*;
import java.util.regex.*;
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

import chemaxon.formats.*;
import chemaxon.struc.*;
import chemaxon.marvin.util.*;
import chemaxon.marvin.io.*; //MolExportException
import chemaxon.util.MolHandler;
import chemaxon.sss.search.SearchException;

import edu.unm.health.biocomp.db.*; //DBCon
import edu.unm.health.biocomp.badapple.*; //badapple_utils
import edu.unm.health.biocomp.hscaf.*;

/**	HScaf BARD Plugin, from UNM; hierarchical scaffold analysis.

	Provides:
	<UL>
	  <LI>Scaffold analysis computation (hscaf package)
	  <LI>Access to stored scaffold analysis data (badapple package)
	  <LI>Scaffold hierarchy info  (hscaf and badapple packages)
	</UL>
	<br/>
	Conceptually, the scaffold analysis provided by this plugin and the Hscaf package
	is a prerequisite for Badapple analysis, and thus should not need to call
	Badapple functions.  However, since Badapple includes stored scaffold analysis, using
	this stored information is advantageous and reasonable .
	<br/>
	Resources:
	<UL>
	  <LI>/cid/{cid} <br/>
		scaffold information for db compound identified by CID
	  <LI>/scafid/{scafid} <i>NOT YET IMPLEMENTED</i> <br/>
		scaffold information for db scaffold identified by SCAFID (e.g. child scaffolds)
	  <LI>/cid/{cid}/scafid/{scafid} <br/>
		compound-scaffold relationship information for CID, SCAFID
	  <LI>/analyze/{smiles} <br/>
		analyze molecule specified by smiles
	</UL>
	<br/>
	Example URIs:
	<UL>
	  <LI>/hscaf/cid/752424
	  <LI>/hscaf/cid/752424/scafid/1809?expand=true
	  <LI>/hscaf/analyze/CC1=C(C(=NO1)C)C2=CC3=C(C=C2)N=CN=C3NCCC4=CNC5=C4C=C(C=C5)OC
	</UL>
	<br/>
	Ref: <a href="http://code.google.com/p/unm-biocomp-hscaf/">Gcode open source project UNM-Biocomp-Hscaf</a>
	<br/>
	@author	Jeremy Yang
	@see	gov.nih.ncgc.bard.plugin.IPlugin
*/
@Path("/hscaf")
public class hscaf_bardPlugin implements IPlugin, ServletContextListener
{
  private String NOTE=null;            // configured via web.xml (init-param)

  /**	static since belong to class/context, not object/servlet. */
  private static String PLUGIN_VERSION="1.0 (Mar 2014)";
  private static PluginManifest PLUGIN_MANIFEST = null;
  private static String DESCRIPTION = null;
  private static String[] RESOURCE_PATHS = null;

  private static String HSCAF_DBTYPE="";	// configured via web.xml (context-param)
  private static String HSCAF_DBHOST=null;	// configured via web.xml (context-param)
  private static Integer HSCAF_DBPORT=null;	// configured via web.xml (context-param)
  private static String HSCAF_DBNAME=null;	// configured via web.xml (context-param)
  private static String HSCAF_DBSCHEMA=null;	// configured via web.xml (context-param)
  private static String HSCAF_DBUSR=null;	// configured via web.xml (context-param)
  private static String HSCAF_DBPW=null;	// configured via web.xml (context-param)

  /**   Persistent connection[s], static since belong to class/context, not object/servlet. */
  private static DBCon DBCON = null;

  public hscaf_bardPlugin() {} // for validation only

  /**	Normal constructor used to read config params from web.xml.
  */
  public hscaf_bardPlugin(
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
  @Path("/_info")
  @Produces(MediaType.TEXT_PLAIN)
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

  /**	This method queries the database containing HSCAF data
	for the compound ID specified and returns scaffold info.
	<br>
	Note the corresponding badapple_bardPlugin prom URI returns a superset of this data and
	includes promiscuity scores.
	<br>
	JSON Response returned.
  */
  @GET
  @Path("/cid/{cid}")
  @Produces({MediaType.APPLICATION_JSON,MediaType.TEXT_PLAIN})
  public Response cidHscaf_JSON(
	@PathParam("cid") Long cid,
	@QueryParam("expand") String expand,
	@QueryParam("repr") String repr,
	@QueryParam("debug") String debug)
  {
    ArrayList<ScaffoldScore> scores=null;
    String smiles=null; //compound smiles
    try {
      scores=badapple_utils.GetScaffoldScoresForDBMol(DBCON,HSCAF_DBSCHEMA,cid,0);
      smiles=badapple_utils.CID2Smiles(DBCON,HSCAF_DBSCHEMA,cid,false);
    }
    catch (SQLException e) { throw new WebApplicationException(e, 503); } //service-unavailable
    catch (Exception e) { throw new WebApplicationException(e, 500); } //server-error
    if (scores==null) throw new WebApplicationException(404); //not-found


    JSONObject jsonob = new JSONObject();
    try {
      jsonob.put("cid",cid);
      jsonob.put("smiles",smiles);
      for (ScaffoldScore score: scores)
      {
        JSONObject jsonob2 = new JSONObject();
        jsonob2.put("scafid",score.getID());
        jsonob2.put("smiles",score.getSmiles());
        jsonob.append("hscafs",jsonob2);
      }
    } catch (JSONException e) {
      throw new WebApplicationException(e, 500);
    }
    return Response.ok(jsonob.toString(), MediaType.APPLICATION_JSON).build();
  }

  /**	This method analyzes the query molecule specified by smiles,
	returns the contained scaffolds and their hierarchy.
	<br>
	JSON Response returned.
	<br>
	This should be revised to return the database scafids if present.
  */
  @GET
  @Path("/analyze/{smiles}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response smiHscaf_JSON(
	@PathParam("smiles") String smiles,
	@QueryParam("expand") String expand,
	@QueryParam("repr") String repr,
	@QueryParam("debug") String debug)
  {
    if (smiles==null) throw new WebApplicationException();

    ScaffoldTree scaftree = Smi2ScaffoldTree(smiles);

    org.json.JSONObject jsonob = new JSONObject();
    try
    {
      jsonob.put("smiles",smiles);
      for (Scaffold scaf: scaftree.getScaffolds())
      {
        JSONObject jsonob2 = new JSONObject();
        if (expand!=null) 
        {
          Long scafid=null;
          try { scafid=badapple_utils.GetScaffoldID(DBCON,HSCAF_DBSCHEMA,scaf.getCansmi(),0); }
          catch (Exception e) { } //Not in db.
          jsonob2.put("scafid",scafid);
          scaf.setID(scafid);
        }
        jsonob2.put("smiles",scaf.getCansmi());
        jsonob.append("hscafs",jsonob2);
      }
      if (expand!=null) 
        jsonob.put("scaftree",scaftree.toString());
    }
    catch (JSONException e) {
      throw new WebApplicationException(e, 500);
    }
    return Response.ok(jsonob.toString(), MediaType.APPLICATION_JSON).build();
  }

  /**	Construct ScaffoldTree for input molecule/smiles.
  */
  private static ScaffoldTree Smi2ScaffoldTree(String smiles)
  {
    Molecule mol=null;
    String stereo="false"; // maybe parameter
    String keep_nitro_attachments="false"; // maybe parameter
    Integer n_frag=null;
    Integer n_ring=null;
    ScaffoldTree scaftree=null;

    try {
      mol=MolImporter.importMol(smiles,"smiles:");
      n_ring=hier_scaffolds_utils.RawRingsystemCount(mol);
      n_frag = mol.getFragCount();
      if (n_frag>1)
        mol=hier_scaffolds_utils.LargestPart(mol);
      scaftree = new ScaffoldTree(mol,
        stereo.equalsIgnoreCase("true"),
        keep_nitro_attachments.equalsIgnoreCase("true"),
	(new ScaffoldSet())); //Local scafIDs assigned.
    }
    catch (Exception e) { throw new WebApplicationException(e, 500); }
    return scaftree;
  }

  /**	Returns data relating a compound to a scaffold, or not-found code if none.
	<br/>
	JSON Response returned.
  */
  @GET
  @Path("/cid/{cid}/scafid/{scafid}")
  @Produces({MediaType.APPLICATION_JSON,MediaType.TEXT_PLAIN})
  public Response cid_scafid_JSON(
	@PathParam("cid") Long cid,
	@PathParam("scafid") Long scafid,
	@QueryParam("expand") String expand,
	@QueryParam("repr") String repr,
	@QueryParam("debug") String debug)
  {
    ArrayList<ScaffoldScore> scores=null;
    String smiles=null;
    try {
      scores=badapple_utils.GetScaffoldScoresForDBMol(DBCON,HSCAF_DBSCHEMA,cid,0);
      smiles=badapple_utils.CID2Smiles(DBCON,HSCAF_DBSCHEMA,cid,false); //BADAPPLE way
    }
    catch (SQLException e) { throw new WebApplicationException(e, 503); } //service-unavailable
    catch (Exception e) { throw new WebApplicationException(e, 500); } //server-error
    if (scores==null) throw new WebApplicationException(404); // not-found

    JSONObject jsonob = null;
    for (ScaffoldScore score: scores)
    {
      if (score.getID()==scafid)
      {
        //smiles=cid2smi(cid); //BARD-REST-API way
        String scafsmi=score.getSmiles();
        String matchsmi=null;
        if (expand!=null) 
        {
          try {
            matchsmi=hier_scaffolds_utils.SubMatchMapSmiles(smiles,scafsmi);
          } catch (MolFormatException e) {
            throw new WebApplicationException(e, 500);
          } catch (SearchException e) {
            throw new WebApplicationException(e, 500);
          } catch (MolExportException e) {
            throw new WebApplicationException(e, 500);
          } catch (IOException e) {
            throw new WebApplicationException(e, 500);
          }
        }
        try {
          jsonob = new JSONObject();
          jsonob.put("cid",cid);
          jsonob.put("smiles",smiles);
          jsonob.put("scafid",scafid);
          jsonob.put("scafsmi",scafsmi);
          if (expand!=null) 
            jsonob.put("matchsmi",matchsmi);
        } catch (JSONException e) {
          throw new WebApplicationException(e, 500);
        }
      }
    }
    if (jsonob==null)
      throw new WebApplicationException(404); //not-found
    return Response.ok(jsonob.toString(), MediaType.APPLICATION_JSON).build();
  }

  /////////////////////////////////////////////////////////////////////////////
  /**	This method queries the database containing HSCAF data.
  */
  @GET
  @Path("/scafid/{scafid}")
  @Produces({MediaType.APPLICATION_JSON,MediaType.TEXT_PLAIN})
  public Response scafidHscaf_JSON(
	@PathParam("scafid") Long scafid,
	@QueryParam("expand") String expand,
	@QueryParam("repr") String repr,
	@QueryParam("debug") String debug)
  {
    String smiles=null; //scaf smiles
    String scaftree=null; //scaftree
    try {
      smiles=badapple_utils.GetScaffoldSmiles(DBCON,HSCAF_DBSCHEMA,scafid,0);
      scaftree=badapple_utils.GetScaffoldTree(DBCON,HSCAF_DBSCHEMA,scafid,0);
    }
    catch (SQLException e) { throw new WebApplicationException(e, 503); } //service-unavailable //Not found?
    catch (Exception e) { throw new WebApplicationException(e, 500); } //server-error

    JSONObject jsonob = new JSONObject();
    try {
      jsonob.put("scafid",scafid);
      jsonob.put("smiles",smiles);
      scaftree=scaftree.replaceAll(":",""); //kludge (discontinuing colons in format)
      jsonob.put("scaftree",scaftree);
      String[] scafids_str = Pattern.compile("[:\\(\\),]+").split(scaftree);
      for (String scafid_str: scafids_str)
      {
        if (scafid_str.isEmpty()) continue;
        JSONObject jsonob2 = new JSONObject();
        Long id=null;
        try { id=Long.parseLong(scafid_str); } catch (Exception e) { continue; }
        if (id.equals(scafid)) continue;
        jsonob2.put("scafid",id);
        if (expand!=null) 
        {
          String csmi=null;
          try { csmi=badapple_utils.GetScaffoldSmiles(DBCON,HSCAF_DBSCHEMA,id,0); }
          catch (Exception e) { continue; }
          jsonob2.put("smiles",csmi);
        }
        jsonob.append("childscafs",jsonob2);
      }
    } catch (JSONException e) {
      throw new WebApplicationException(e, 500);
    }
    return Response.ok(jsonob.toString(), MediaType.APPLICATION_JSON).build();
  }

  /////////////////////////////////////////////////////////////////////////////
  private void initializeManifest()
  {
    PLUGIN_MANIFEST = new PluginManifest();
    PLUGIN_MANIFEST.setAuthor("Jeremy Yang");
    PLUGIN_MANIFEST.setAuthorEmail("jjyang@salud.unm.edu");
    PLUGIN_MANIFEST.setMaintainer(PLUGIN_MANIFEST.getAuthor());
    PLUGIN_MANIFEST.setMaintainerEmail(PLUGIN_MANIFEST.getAuthorEmail());
    PLUGIN_MANIFEST.setTitle("HScaf scaffold analysis");
    PLUGIN_MANIFEST.setDescription("A brief description will go here");
    PLUGIN_MANIFEST.setVersion(PLUGIN_VERSION);

    ArrayList<PluginResource> pluginResources = new ArrayList<PluginResource>();
    PluginResource res = new PluginResource();
    res.setPath("/cid/{cid}");
    res.setMimetype(MediaType.APPLICATION_JSON);
    res.setMethod("GET");
    res.setArgs(new PathArg[]{
	new PathArg("cid","integer","path"),
	new PathArg("expand","boolean","query")
	});
    pluginResources.add(res);
    res = new PluginResource();
    res.setPath("/cid/{cid}/scafid/{scafid}");
    res.setMimetype(MediaType.APPLICATION_JSON);
    res.setMethod("GET");
    res.setArgs(new PathArg[]{
	new PathArg("cid", "integer","path"),
	new PathArg("scafid","integer","path"),
	new PathArg("expand","boolean","query")
	});
    pluginResources.add(res);
    res = new PluginResource();
    res.setPath("/analyze/{smiles}");
    res.setMimetype(MediaType.APPLICATION_JSON);
    res.setMethod("GET");
    res.setArgs(new PathArg[]{
	new PathArg("smiles","string","path"),
	new PathArg("expand","boolean","query")
	});
    pluginResources.add(res);
    res = new PluginResource();
    res.setPath("/scafid/{scafid}");
    res.setMimetype(MediaType.APPLICATION_JSON);
    res.setMethod("GET");
    res.setArgs(new PathArg[]{
	new PathArg("scafid","integer","path"),
	new PathArg("expand","boolean","query")
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
    HSCAF_DBTYPE=servletContext.getInitParameter("HSCAF_DBTYPE");
    if (HSCAF_DBTYPE==null)
    {
      servletContext.log("problem reading HSCAF_DBTYPE: "+HSCAF_DBTYPE);
      return;
    }
    HSCAF_DBHOST=servletContext.getInitParameter("HSCAF_DBHOST");
    if (HSCAF_DBHOST==null) HSCAF_DBHOST="localhost";
    try { HSCAF_DBPORT=Integer.parseInt(servletContext.getInitParameter("HSCAF_DBPORT")); }
    catch (Exception e) { HSCAF_DBPORT=5432; } //NA for Derby
    HSCAF_DBNAME=servletContext.getInitParameter("HSCAF_DBNAME");
    if (HSCAF_DBNAME==null) HSCAF_DBNAME="/usr/local/tomcat/webapps/bardplugin_hscaf/derby/hscaf";
    HSCAF_DBSCHEMA=servletContext.getInitParameter("HSCAF_DBSCHEMA");
    if (HSCAF_DBSCHEMA==null) HSCAF_DBSCHEMA="APP"; //Derby default
    HSCAF_DBUSR=servletContext.getInitParameter("HSCAF_DBUSR");
    if (HSCAF_DBUSR==null) HSCAF_DBUSR="bard"; //NA for Derby
    HSCAF_DBPW=servletContext.getInitParameter("HSCAF_DBPW");
    if (HSCAF_DBPW==null) HSCAF_DBPW="stratford"; //NA for Derby
    try {
      DBCON = new DBCon(HSCAF_DBTYPE,HSCAF_DBHOST,HSCAF_DBPORT,HSCAF_DBNAME,HSCAF_DBUSR,HSCAF_DBPW);
      servletContext.log("DEBUG: db connection ok: "+HSCAF_DBTYPE);
    }
    catch (Exception e) {
      servletContext.log("ERROR: db connection failed: "+HSCAF_DBTYPE);
    }

    /**	Create plugin manifest. */
    this.initializeManifest();

    /**	Create description string. */
    StringBuilder msg = new StringBuilder("HScaf BARD Plugin, from UNM; hierarchical scaffold analysis.\n");
    msg.append("VERSION: "+PLUGIN_VERSION+"\n");
    msg.append("UNM BARD Team: Jeremy Yang, Anna Waller, Cristian Bologa, Oleg Ursu, Steve Mathias, Tudor Oprea, Larry Sklar\n");
    msg.append("database ("+HSCAF_DBTYPE+"): "+HSCAF_DBHOST+":"+HSCAF_DBPORT+":"+HSCAF_DBNAME+(HSCAF_DBTYPE.equalsIgnoreCase("postgres")?(":"+HSCAF_DBSCHEMA):"")+"\n");
    msg.append("NOTE: "+NOTE+"\n");
    if (DBCON==null) msg.append("DEBUG: DBCON==null.  Aaack!\n");
    msg.append("\nAvailable resources:\n");
    HashSet<String> paths_set = new HashSet<String>(); // for deduplication
    for (String path: Util.getResourcePaths(this.getClass())) paths_set.add(path);
    String[] paths = paths_set.toArray(new String[0]);
    Arrays.sort(paths);
    for (String path: paths) msg.append(path).append("\n");
    try { msg.append("range(scafid): [1,"+badapple_utils.GetMaxScafID(DBCON,HSCAF_DBSCHEMA)+"]"); }
    catch (Exception e) { msg.append("ERROR: JDBC connection failed: "+e.getMessage()); }
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
        ok=pv.validate(hscaf_bardPlugin.class,null);
    } catch (InstantiationException e) {
      System.out.println("ERROR: (InstantiationException) "+e.getMessage());
    }
    System.out.println("validation status: "+(ok?"PASS":"FAIL"));
    for (String s: pv.getErrors()) System.out.println(s);
  }
}
