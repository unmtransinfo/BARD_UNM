package edu.unm.health.biocomp.bard.util;

import java.io.*;
import java.util.*;
import java.net.*;

import org.json.*;

import edu.unm.health.biocomp.util.*;

/**	Simple BARD API client.

	@author	Jeremy Yang
*/
public class bard_query
{
  private static String BARD_API_HOST=bard_api_utils.BARD_API_HOST;
  private static String BARD_API_VERSION=bard_api_utils.BARD_API_VERSION;
  private static String BARD_API_BASEPATH=bard_api_utils.BARD_API_BASEPATH;
  private static List<String> RESOURCES=Arrays.asList("projects","assays","experiments","compounds","substances","probes","documents");
  private static void Help(String msg)
  {
    System.err.println(msg+"\n"
      +"bard_query - query BARD REST API\n"
      +"\n"
      +"usage: bard_query [options]\n"
      +"  required (one of):\n"
      +"    -counts ..................... resource _counts\n"
      +"    -describe ................... describe API (_infos)\n"
      +"    -getprojects ................ projects to output CSV\n"
      +"    -cpdsmi ..................... find SMILES for CID[s]\n"
      +"    -cpddata .................... find data for CID[s]\n"
      +"  options:\n"
      +"    -o OUTFILE .................. output file\n"
      +"    -api_host API_HOST .......... ["+BARD_API_HOST+"]\n"
      +"    -api_version API_VERSION .... ["+BARD_API_VERSION+"]\n"
      +"    -id ID ...................... input ID\n"
      +"    -i INFILE ................... input IDs\n"
      +"    -v, -vv, -vvv ............... verbose [very [very]]\n"
      +"    -h .......................... this help\n");
    System.exit(1);
  }
  private static int verbose=0;
  private static String ifile=null;
  private static Long id_query=null;
  private static String ofile=null;
  private static boolean counts=false;
  private static boolean describe=false;
  private static boolean cpdsmi=false;
  private static boolean cpddata=false;
  private static boolean getprojects=false;

  /////////////////////////////////////////////////////////////////////////////
  private static void ParseCommand(String args[])
  {
    for (int i=0;i<args.length;++i)
    {
      if (args[i].equals("-i")) ifile=args[++i];
      else if (args[i].equals("-o")) ofile=args[++i];
      else if (args[i].equals("-counts")) counts=true;
      else if (args[i].equals("-describe")) describe=true;
      else if (args[i].equals("-getprojects")) getprojects=true;
      else if (args[i].equals("-cpdsmi")) { cpdsmi=true; }
      else if (args[i].equals("-cpddata")) { cpddata=true; }
      else if (args[i].equals("-id")) { id_query=Long.parseLong(args[++i]); }
      else if (args[i].equals("-api_host")) BARD_API_HOST=args[++i];
      else if (args[i].equals("-api_version")) BARD_API_VERSION=args[++i];
      else if (args[i].equals("-v")) verbose=1;
      else if (args[i].equals("-vv")) verbose=2;
      else if (args[i].equals("-vvv") || args[i].equals("-debug")) verbose=3;
      else if (args[i].equals("-h")) Help("");
      else Help("Unknown option: "+args[i]);
    }
  }

  /**	
  */
  public static void main(String[] args)
    throws IOException
  {
    ParseCommand(args);
    if (getprojects && ofile==null) Help("-getprojects requires -o OUTFILE.");

    String BARD_API_BASE_URI=bard_api_utils.BaseURI(BARD_API_HOST,BARD_API_BASEPATH,BARD_API_VERSION);

    if (verbose>0)
      System.err.println("BARD_API_BASE_URI: "+BARD_API_BASE_URI);

    java.util.Date t_0 = new java.util.Date();

    long n_err=0;

    ArrayList<Long> ids_query = new ArrayList<Long>();
    if (ifile!=null)
    {
      BufferedReader breader = new BufferedReader(new FileReader(ifile));
      String line=null;
      while ((line=breader.readLine())!=null)
      {
        try {
          Long id = Long.parseLong(line);
          ids_query.add(id);
        }
        catch (Exception e) { continue; }
      }
      breader.close();
      System.err.println("IDs read: "+ids_query.size());
    }

    JSONObject jsonob = new JSONObject();

    if (counts)
    {
      String txt="";
      for (String res: RESOURCES)
      {
        try {
          bard_api_utils.GetURI(BARD_API_BASE_URI+"/"+res+"/_count",null,jsonob,verbose);
          long n=jsonob.getLong("value");
          txt+=("\t"+res+": "+n+"\n");
        }
        catch (Exception e) { System.err.println("Exception: "+e.getMessage()); }
      }
      System.out.println(txt);
    }
    else if (describe)
    {
      String txt="";
      for (String res: RESOURCES)
      {
        txt+=res+":\n";
        try {
          bard_api_utils.GetURI(BARD_API_BASE_URI+"/"+res+"/_info",null,jsonob,verbose);
          txt+=(jsonob.getLong("value")+"\n");
        }
        catch (Exception e) { System.err.println("Exception: "+e.getMessage()); }
      }
      System.out.println(txt);
    }
    else if (cpdsmi)
    {
      if (id_query!=null)
      {
        String smi="";
        try { smi=bard_api_utils.Cid2Smiles(BARD_API_BASE_URI,id_query,verbose); }
        catch (Exception e) { System.err.println("Exception: "+e.getMessage()); }
        System.out.println(smi+" "+id_query);
      }
      else if (ids_query.size()>0)
      {
        System.err.println("ERROR: not impl yet...");
      }
      else System.err.println("ERROR: requires -id or -idfile");
    }
    else if (cpddata)
    {
      if (id_query!=null)
      {
        try {
          jsonob=bard_api_utils.Cid2Data(BARD_API_BASE_URI,id_query,verbose);
          for (String tag: JSONObject.getNames(jsonob))
            System.out.println(tag+": \""+jsonob.getString(tag)+"\"");
        }
        catch (Exception e) { System.err.println("Exception: "+e.getMessage()); }
      }
      else if (ids_query.size()>0)
      {
        try {
          JSONArray jsonarray=bard_api_utils.Cids2Data(BARD_API_BASE_URI,ids_query,verbose);
        }
        catch (Exception e) { System.err.println("Exception: "+e.getMessage()); }
        System.err.println("ERROR: not impl yet...");
      }
      else System.err.println("ERROR: requires -id or -idfile");
    }
    else if (getprojects)
    {
      File fout = new File(ofile);
      long n_out=0;
      try { n_out=bard_api_utils.GetProjects(BARD_API_BASE_URI,fout,verbose); }
      catch (Exception e) { System.err.println("Exception: "+e.getMessage()); }
    }
    else
    {
      Help("ERROR: no operation specified.");
    }

    System.err.println("n_err: "+n_err);
    if (verbose>0)
      System.err.println("total elapsed: "+time_utils.TimeDeltaStr(t_0,new java.util.Date()));
  }

}
