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

import edu.unm.health.biocomp.hscaf.*;
import edu.unm.health.biocomp.util.*;

/**
	From output of hier_scaffolds and BARD queries, determine scaffold activity stats.

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
	   <li>scafid
	   <li>cTot - total no of compounds with that scaf in the library
	   <li>cTested - how many compounds with that scaf have been tested
	   <li>cActive - how many compounds with that scaf have been tested active
	   <li>aTested - how many assays where that scaf has been tested
	   <li>aActive - how many assays where that scaf has been tested active
	   <li>sTested - how many samples with that scaf have been tested
	   <li>sActive - how many samples with that scaf have been tested active
	</ul>

	   Also needed: median(cTested), median(aTested) and median(sTested) for all scaffolds

	@author	Jeremy Yang
*/
public class hscaf_assaystats
{
  private static final String HTTP_HOST="assay.nih.gov";
  private static final String BASE_PATH="/bard/rest/v1";
  private static final String BASE_URI="http://"+HTTP_HOST+BASE_PATH;

  private static void help(String msg)
  {
    System.err.println(msg+"\n"
      +"hscaf_assaystats - annotates HierS scaffolds w/ related activity data\n"
      +"\n"
      +"usage: hscaf_assaystats [options]\n"
      +"  required:\n"
      +"    -i INSCAFS .................. input scaffolds file (from hier_scaffolds)\n"
      +"    -inmols INMOLS .............. smiles w/ scafids from hier_scaffolds (from hier_scaffolds)\n"
      +"    -o OUTSCAFS ................. output scafs with data\n"
      +"  options:\n"
      +"    -v .......................... verbose\n"
      +"    -vv ......................... very verbose\n"
      +"    -h .......................... this help\n");
    System.exit(1);
  }
  private static int verbose=0;
  private static String ifile_scaf=null;
  private static String ifile_mol=null;
  private static String ofile_scaf=null;

  /////////////////////////////////////////////////////////////////////////////
  private static void parseCommand(String args[])
  {
    for (int i=0;i<args.length;++i)
    {
      if (args[i].equals("-i")) ifile_scaf=args[++i];
      else if (args[i].equals("-inmols")) ifile_mol=args[++i];
      else if (args[i].equals("-o")) ofile_scaf=args[++i];
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
    if (ifile_scaf==null || ifile_mol==null) help("-i and -inmols required.");

    if (!MFileFormatUtil.getMostLikelyMolFormat(ifile_scaf).equals("smiles"))
      help("ERROR: input format not supported; SMILES (.smi) required.");
    if (!(new File(ifile_scaf).exists())) help("Non-existent input file: "+ifile_scaf);
    MolImporter molReader_scaf = new MolImporter(ifile_scaf);
    if (!MFileFormatUtil.getMostLikelyMolFormat(ifile_mol).equals("smiles"))
      help("ERROR: input format not supported; SMILES (.smi) required.");
    if (!(new File(ifile_mol).exists())) help("Non-existent input file: "+ifile_mol);
    MolImporter molReader_mol = new MolImporter(ifile_mol);
    if (ofile_scaf==null) help("-o required.");

    MolExporter molWriter_scaf=null;
    String ofmt=MFileFormatUtil.getMostLikelyMolFormat(ofile_scaf);
    if (ofmt.equals("smiles")) ofmt="smiles:+n-a"; //Kekule for compatibility
    else System.err.println("Warning: output format \""+ofmt+"\" not fully supported.");
    molWriter_scaf=new MolExporter(new FileOutputStream(ofile_scaf),ofmt);

    if (verbose>1)
      System.err.println("JChem version: "+chemaxon.jchem.version.VersionInfo.getVersion());

    // First read molecule file and attach list of cids to each scaf

    LinkedHashMap<Integer,ArrayList<Integer> > scafs = new LinkedHashMap<Integer,ArrayList<Integer> >();
    Molecule mol;
    int n_mol=0;
    int n_err=0;
    int n_noscaf=0;
    int n_total_scaf=0;
    java.util.Date t_0 = new java.util.Date();
    if (verbose>0)
      System.err.println(DateFormat.getDateTimeInstance().format(t_0));
    java.util.Date t_i = t_0;
    for (n_mol=0;true;)
    {
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
      //if (nskip>0 && n_mol<=nskip) continue;

      String molname=mol.getName();
      if (verbose>1)
      {
        System.err.println(""+n_mol+". "+molname);
        try { System.err.println("\t"+mol.exportToFormat("smiles:+n-a")); }
        catch (MolExportException e) { System.err.println(e.getMessage()); }
      }
      // molname should have format: <CID><space>S:[ScafID01,ScafID02,...]

      if (!molname.matches("[0-9]+\\s+S:.*$"))
      {
        if (verbose>1)
          System.err.println("ERROR: Bad line (no hier_scaffolds output) ["+n_mol+"] "+molname);
        ++n_noscaf;
        continue;
      }
      String[] fields=Pattern.compile("\\s+").split(molname);
      if (fields.length!=2)
      {
        System.err.println("ERROR: Bad line (no hier_scaffolds output) ["+n_mol+"]; nfields!=2 ; "+molname);
        ++n_noscaf;
        continue;
      }

      Integer cid=null;
      try {
        cid=Integer.parseInt(fields[0]);
      }
      catch (Exception e) {
        ++n_err;
        System.err.println("ERROR: Bad line ["+n_mol+"]; could not parse CID; "+molname);
        continue;
      }

      String[] scafids_str=Pattern.compile(",").split(fields[1].replaceFirst("^S:",""));
      for (String scafid_str: scafids_str)
      {
        if (scafid_str.isEmpty()) continue;
        Integer scafid=null;
        try {
          scafid=Integer.parseInt(scafid_str);
          if (!scafs.containsKey(scafid))
            scafs.put(scafid,new ArrayList<Integer>());
          scafs.get(scafid).add(cid);
        }
        catch (Exception e)
        { System.err.println("ERROR: ["+n_mol+"]; could not parse ScafID (\""+scafid_str+"\"); "+molname); }
      }
    }
    molReader_mol.close();
    System.err.println("mols read: "+n_mol+"; scaf count: "+scafs.size());
    System.err.println("mols missing scaffold data: "+n_noscaf);
    System.err.println("errors: "+n_err);
    if (verbose>0)
      System.err.println("elapsed: "+time_utils.TimeDeltaStr(t_0,new java.util.Date()));

    // Read scaf file, process scafs in sequence, write annotated scaf file.
    // Loop over scafids and cids.
    // For each cid, fetch aids and outcomes.
    // (Non-active may be inactive, discrepant, etc.).
    // (This may be inefficient since each cid may be queried many times.)
    Molecule scafmol;
    int n_scaf=0;
    int n_result_total=0;
    LinkedHashMap<Long,Boolean> expts_global = new LinkedHashMap<Long,Boolean>();
    ArrayList<Integer> cTesteds = new ArrayList<Integer>();
    ArrayList<Integer> aTesteds = new ArrayList<Integer>();
    ArrayList<Integer> sTesteds = new ArrayList<Integer>();
    for (n_scaf=0;true;)
    {
      java.util.Date t_this_0 = new java.util.Date();
      boolean ok=true;
      try { scafmol=molReader_scaf.read(); }
      catch (MolFormatException e)
      {
        System.err.println(e.getMessage());
        ++n_err;
        continue;
      }
      if (scafmol==null) break; //EOF
      ++n_scaf;

      String scafmolname=scafmol.getName();
      if (verbose>1)
      {
        System.err.println(""+n_scaf+". "+scafmolname);
        try { System.err.println("\t"+scafmol.exportToFormat("smiles:+n-a")); }
        catch (MolExportException e) { System.err.println(e.getMessage()); }
      }
      // scafmolname should have format: <SCAFID><space><SCAFTREESTR>

      if (!scafmolname.matches("[0-9]+\\s+[0-9:(,)]+$"))
      {
        System.err.println("ERROR: Bad line ["+n_scaf+"] "+scafmolname);
        ++n_err;
        continue;
      }
      String[] fields=Pattern.compile("\\s+").split(scafmolname);
      if (fields.length!=2)
      {
        System.err.println("ERROR: Bad line ["+n_scaf+"]; nfields!=2 ; "+scafmolname);
        ++n_err;
        continue;
      }

      Integer scafid=null;
      try {
        scafid=Integer.parseInt(fields[0]);
      }
      catch (Exception e) {
        ++n_err;
        System.err.println("ERROR: Bad line ["+n_scaf+"]; could not parse ScafID; "+scafmolname);
        continue;
      }

      if (!scafs.containsKey(scafid))
      {
        ++n_err;
        System.err.println("ERROR: ScafID missing ("+scafid+").");
        continue;
      }

      int cTotal=0;
      int cTested=0;
      int cActive=0;
      int aTested=0;
      int aActive=0;
      int sTested=0;
      int sActive=0;
      LinkedHashMap<Long,Boolean> expts = new LinkedHashMap<Long,Boolean>();
      LinkedHashMap<Long,Boolean> expts_active = new LinkedHashMap<Long,Boolean>();
      for (Integer cid:scafs.get(scafid))
      {
        ++cTotal;
        LinkedHashMap<Long,ArrayList<ExptResult> > exptData = Cid2ExptData(cid);
        if (!exptData.isEmpty()) ++cTested;
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
            if (n_result_total%100==0) { System.err.print("."); System.err.flush(); }//DEBUG-progress
            if (n_result_total%5000==0) System.err.println(" ("+n_result_total+" results)"); //DEBUG-progress
          }
        }
        if (isActive) ++cActive;
      }
      aTested=expts.size();
      aActive=expts_active.size();

      // Output format: smiles scafid cTotal cTested cActive aTested aActive sTested sActive
      System.err.println("DEBUG: "+String.format("%s scafID=%d cTotal=%d cTested=%d cActive=%d aTested=%d aActive=%d sTested=%d sActive=%d",scafmol.exportToFormat("smiles:u"),scafid,cTotal,cTested,cActive,aTested,aActive,sTested,sActive));
      scafmol.setName(String.format("%d %d %d %d %d %d %d %d",
        scafid,cTotal,cTested,cActive,aTested,aActive,sTested,sActive));
      molWriter_scaf.write(scafmol);
      if (verbose>1 && n_scaf%10==0)
      {
        System.err.println("elapsed: "+time_utils.TimeDeltaStr(t_0,new java.util.Date()));
      }
      for (long eid: expts.keySet()) expts_global.put(eid,true);
      cTesteds.add(cTested);
      aTesteds.add(aTested);
      sTesteds.add(sTested);

      System.err.println("DEBUG: elapsed: "+time_utils.TimeDeltaStr(t_0,new java.util.Date()));
      //if (n_scaf==10) break; //DEBUG
    }
    if (!cTesteds.isEmpty())
    {
      Collections.sort(cTesteds);
      System.err.println("median cTested: "+cTesteds.get(cTesteds.size()/2));
    }
    if (!aTesteds.isEmpty())
    {
      Collections.sort(aTesteds);
      System.err.println("median aTested: "+aTesteds.get(aTesteds.size()/2));
    }
    if (!sTesteds.isEmpty())
    {
      Collections.sort(sTesteds);
      System.err.println("median sTested: "+sTesteds.get(sTesteds.size()/2));
    }

    molReader_scaf.close();
    molWriter_scaf.close();
    System.err.println("n_scaf: "+n_scaf);
    System.err.println("n_mol: "+n_mol);
    System.err.println("n_expt: "+expts_global.size());
    System.err.println("n_result_total: "+n_result_total);
    System.err.println("n_noscaf: "+n_noscaf);
    System.err.println("n_err: "+n_err);
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
        System.err.println("ERROR: HTTP response code: "+response);
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
    for (int i=0;i<exptdata_uris.length();++i)
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
          System.err.println("ERROR: HTTP response code: "+response);
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
