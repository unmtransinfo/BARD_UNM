package edu.unm.health.biocomp.bard.util;

import java.io.*;
import java.util.*;
import java.text.DateFormat;
import java.util.regex.Pattern;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

//import javax.ws.rs.*;
//import javax.ws.rs.core.*;

import org.json.*;

import chemaxon.formats.*;
import chemaxon.sss.search.*;
import chemaxon.struc.*;
import chemaxon.util.MolHandler;
import chemaxon.marvin.io.*;
import com.chemaxon.version.VersionInfo;

import edu.unm.health.biocomp.hscaf.*;
import edu.unm.health.biocomp.util.*;

/**
	Determine BARD compound activity stats.

	PubChem activity outcomes: 
	<ul>
	   <li>1 = inactive
	   <li>2 = active
	   <li>3 = inconclusive
	   <li>4 = unspecified
	   <li>5 = probe
	   <li>multiple, differing 1, 2 or 3 = discrepant
	   <li>not 4 = tested
	</ul>

	Fields output:
	<ul>
	   <li>cid
	   <li>aTested - how many assays where that compound has been tested
	   <li>aActive - how many assays where that compound has been tested active
	   <li>sTested - how many samples with that compound have been tested
	   <li>sActive - how many samples with that compound have been tested active
	</ul>

	@author	Jeremy Yang
*/
public class compound_assaystats
{
  private static final String HTTP_HOST="assay.nih.gov";
  private static final String BASE_PATH="/bard/rest/v1";
  private static final String BASE_URI="http://"+HTTP_HOST+BASE_PATH;

  private static void help(String msg)
  {
    System.err.println(msg+"\n"
      +"compound_assaystats - dump BARD compounds annotated with activity stats\n"
      +"\n"
      +"usage: compound_assaystats [options]\n"
      +"  required:\n"
      +"    -i INMOLS ................... input compounds with CIDs in name field\n"
      +"    -o OUTMOLS .................. output compounds with stats\n"
      +"  options:\n"
      +"    -nmax NMAX ................ quit after NMAX molecules\n"
      +"    -nskip NSKIP .............. skip NSKIP molecules\n"
      +"    -v .......................... verbose\n"
      +"    -vv ......................... very verbose\n"
      +"    -h .......................... this help\n");
    System.exit(1);
  }
  private static int verbose=0;
  private static String ifile_mol=null;
  private static String ofile_mol=null;
  private static int nmax=0;
  private static int nskip=0;

  /////////////////////////////////////////////////////////////////////////////
  private static void parseCommand(String args[])
  {
    for (int i=0;i<args.length;++i)
    {
      if (args[i].equals("-i")) ifile_mol=args[++i];
      else if (args[i].equals("-o")) ofile_mol=args[++i];
      else if (args[i].equals("-nmax")) nmax=Integer.parseInt(args[++i]);
      else if (args[i].equals("-nskip")) nskip=Integer.parseInt(args[++i]);
      else if (args[i].equals("-v")) verbose=1;
      else if (args[i].equals("-vv")) verbose=2;
      else if (args[i].equals("-vvv") || args[i].equals("-debug")) verbose=3;
      else if (args[i].equals("-h")) help("");
      else help("Unknown option: "+args[i]);
    }
  }

  /**	
  */
  public static void main(String[] args)
    throws IOException
  {
    parseCommand(args);

    if (ifile_mol==null) help("-i required.");
    if (!MFileFormatUtil.getMostLikelyMolFormat(ifile_mol).equals("smiles"))
      help("ERROR: input format not supported; SMILES (.smi) required.");
    if (!(new File(ifile_mol).exists())) help("Non-existent input file: "+ifile_mol);
    MolImporter molReader_mol = new MolImporter(ifile_mol);

    MolExporter molWriter_mol=null;
    String ofmt=MFileFormatUtil.getMostLikelyMolFormat(ofile_mol);
    if (ofmt.equals("smiles")) ofmt="smiles:+n-a"; //Kekule for compatibility
    else System.err.println("Warning: output format \""+ofmt+"\" not fully supported.");
    molWriter_mol=new MolExporter(new FileOutputStream(ofile_mol),ofmt);

    if (verbose>1)
      System.err.println("JChem version: "+VersionInfo.getVersion());

    java.util.Date t_0 = new java.util.Date();
    if (verbose>0)
      System.err.println(DateFormat.getDateTimeInstance().format(t_0));
    java.util.Date t_i = t_0;
    
    // Loop over cids.
    // 
    // Since there is currently no way to iterate over all compounds/cids
    // using the BARD REST API, we must use an input file already obtained.
    // 
    // For each cid, fetch aids and outcomes.
    // (Non-active may be inactive, discrepant, etc.).

    Molecule mol;
    int n_mol=0;
    int n_err=0;
    int n_nocid=0;
    int n_tested=0;
    int n_result_total=0;
    LinkedHashMap<Long,Boolean> expts_global = new LinkedHashMap<Long,Boolean>();

    for (n_mol=0;true;)
    {
      java.util.Date t_this_0 = new java.util.Date();
      boolean ok=true;
      try { mol=molReader_mol.read(); }
      catch (MolFormatException e)
      {
        System.err.println(e.getMessage());
        ++n_err;
        continue;
      }
      if (mol==null) break; //EOF
      ++n_mol;
      if (nskip>0 && n_mol<=nskip) continue;

      String molname=mol.getName();

      if (verbose>1)
      {
        System.err.println(""+n_mol+". "+molname);
        try { System.err.println("\t"+mol.exportToFormat("smiles:+n-a")); }
        catch (MolExportException e) { System.err.println(e.getMessage()); }
      }

      if (!molname.matches("[0-9]+.*$"))
      {
        if (verbose>1)
          System.err.println("ERROR: Bad input (no CID in name field) ["+n_mol+"] "+molname);
        ++n_nocid;
        ++n_err;
        continue;
      }

      String[] fields=Pattern.compile("\\s+").split(molname);

      Integer cid=null;
      try {
        cid=Integer.parseInt(fields[0]);
      }
      catch (Exception e) {
        ++n_err;
        System.err.println("ERROR: Bad line ["+n_mol+"]; could not parse CID; "+molname);
        continue;
      }

      int aTested=0;
      int aActive=0;
      int sTested=0;
      int sActive=0;
      LinkedHashMap<Long,Boolean> expts = new LinkedHashMap<Long,Boolean>();
      LinkedHashMap<Long,Boolean> expts_active = new LinkedHashMap<Long,Boolean>();

      LinkedHashMap<Long,ArrayList<ExptResult> > exptData = Cid2ExptData(cid);
      if (!exptData.isEmpty()) ++n_tested;
      boolean isActive=false;
      for (long eid: exptData.keySet())
      {
        expts.put(eid,true);
        ArrayList<ExptResult> results = exptData.get(eid);
        ++sTested;
        for (ExptResult result: results)
        {
          isActive|=result.isActive();
          if (result.isActive())
          {
            expts_active.put(eid,true);
            ++sActive;
          }
          ++n_result_total;
        }
      }

      aTested=expts.size();
      aActive=expts_active.size();

      // Output name format: id aTested aActive sTested sActive
      if (verbose>0) System.err.println(String.format("%s cID=%d aTested=%d aActive=%d sTested=%d sActive=%d",mol.exportToFormat("smiles:u"),cid,aTested,aActive,sTested,sActive));
      if (n_mol%100==0)
      {
        System.err.println("DEBUG: "+n_mol+" mols; "+n_result_total+" results...");
        System.err.println("DEBUG: elapsed: "+time_utils.TimeDeltaStr(t_0,new java.util.Date()));
      }
      mol.setName(String.format("%d %d %d %d %d",cid,aTested,aActive,sTested,sActive));
      molWriter_mol.write(mol);

      for (long eid: expts.keySet()) expts_global.put(eid,true);

      if (nmax>0 && n_mol==(nmax+nskip)) break;
    }

    molWriter_mol.close();
    System.err.println("total input mols: "+n_mol);
    if (nskip>0)
    {
      System.err.println("skipped mols: "+nskip);
      System.err.println("processed mols: "+(n_mol-nskip));
    }
    System.err.println("total experiments : "+expts_global.size());
    System.err.println("total results: "+n_result_total);
    System.err.println("missing/unparseable CIDs): "+n_nocid);
    System.err.println("errors: "+n_err);
    if (verbose>0)
      System.err.println("total elapsed: "+time_utils.TimeDeltaStr(t_0,new java.util.Date()));
  }

  /**	Returned map keys are EIDs; for each EID, value is list of ExptResults.
	
  */
  private static LinkedHashMap<Long,ArrayList<ExptResult> > Cid2ExptData(Integer cid)
  {
    LinkedHashMap<Long,ArrayList<ExptResult> > exptData = new LinkedHashMap<Long,ArrayList<ExptResult> >();
    JSONArray exptdata_uris = null;
    try
    {
      //System.err.println("DEBUG: uri: "+BASE_URI+"/compounds/"+cid+"/exptdata");
      URL url = new URI(BASE_URI+"/compounds/"+cid+"/exptdata").toURL();
      HttpURLConnection hcon = (HttpURLConnection) url.openConnection();
      hcon.connect();
      int response=hcon.getResponseCode();
      if (response==200)
      {
        BufferedReader reader = new BufferedReader(new InputStreamReader(hcon.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String str;
        while ((str=reader.readLine())!=null) sb.append(str);
        reader.close();
        JSONObject jsonob = new JSONObject(sb.toString());
        //System.err.println("DEBUG: jsonob: "+jsonob.toString(2));
        exptdata_uris = jsonob.getJSONArray("collection");
      }
      else
      {
        System.err.println("ERROR: ["+cid+"] HTTP response code: "+response);
      }
    } catch (MalformedURLException e) {
      System.err.println(e.getMessage());
    } catch (URISyntaxException e) {
      System.err.println(e.getMessage());
    } catch (JSONException e) {
      System.err.println(e.getMessage());
    } catch (IOException e) {
      System.err.println(e.getMessage());
    } catch (Exception e) {
      System.err.println(e.getMessage());
    }

    // Around here there was a problem:
    // ERROR: HTTP response code: 302
    // Exception in thread "main" java.lang.NullPointerException
    // at edu.unm.health.biocomp.bard.util.compound_assaystats.Cid2ExptData(compound_assaystats.java:270)
    // at edu.unm.health.biocomp.bard.util.compound_assaystats.main(compound_assaystats.java:184)
    //
    // Probably due to exptdata_uris being null.

    for (int i=0;exptdata_uris!=null && i<exptdata_uris.length();++i)
    {
      try {
        //System.err.println("DEBUG: uri = "+exptdata_uris.getString(i));
        URL url = new URI("http://"+HTTP_HOST+exptdata_uris.getString(i)).toURL();
        HttpURLConnection hcon = (HttpURLConnection) url.openConnection();
        hcon.connect();
        int response=hcon.getResponseCode();
        if (response==200)
        {
          BufferedReader reader = new BufferedReader(new InputStreamReader(hcon.getInputStream()));
          StringBuilder sb = new StringBuilder();
          String str;
          while ((str=reader.readLine())!=null) sb.append(str);
          reader.close();
          JSONObject jsonob = new JSONObject(sb.toString());
          //System.err.println("DEBUG: jsonob: "+jsonob.toString(2));
          long eid=jsonob.getLong("eid");
          //System.err.println(String.format("DEBUG: %d\t%d\t%d\t%d\t%.2f\t%d\t%d\t%s",jsonob.getLong("exptDataId"),jsonob.getLong("sid"),jsonob.getLong("eid"),jsonob.getInt("classification"),jsonob.getDouble("potency"),jsonob.getInt("score"),jsonob.getInt("outcome"),(jsonob.getInt("outcome")==2?"***ACTIVE***":"")));
          if (!exptData.containsKey(eid))
            exptData.put(eid,new ArrayList<ExptResult>());
          exptData.get(eid).add(new ExptResult( jsonob.getLong("exptDataId"), jsonob.getLong("eid"), jsonob.getLong("sid"), cid, jsonob.getInt("classification"), jsonob.getDouble("potency"), jsonob.getInt("score"), jsonob.getInt("outcome")));

        }
        else
        {
          System.err.println("ERROR: ["+cid+"] HTTP response code: "+response);
        }
      } catch (MalformedURLException e) {
        System.err.println(e.getMessage());
      } catch (URISyntaxException e) {
        System.err.println(e.getMessage());
      } catch (JSONException e) {
        System.err.println(e.getMessage());
      } catch (IOException e) {
        System.err.println(e.getMessage());
      }
    }
    return exptData;
  }
}
