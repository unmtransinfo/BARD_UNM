package edu.unm.health.biocomp.bard.restlet;

import java.io.*;
import java.util.*;

import java.sql.*;

import chemaxon.formats.*;
import chemaxon.struc.*;
import chemaxon.util.MolHandler;
import chemaxon.sss.search.SearchException;

import org.restlet.*;
import org.restlet.resource.*;
import org.restlet.data.*;
import org.restlet.representation.*;
import org.restlet.util.*;

import org.json.*;
import org.restlet.ext.json.*;

/**	BADAPPLE scaffold promiscuity db ServerResource.  Invoked by Application.
	Given molecule smiles, lookup scaffold promiscuity statistics;
	calculate scores.
	<br>
	Test:
	<br>
	  &nbsp; <code><b>wget -q -O - 'http://localhost:8080/jjy/bard/db/badapple/prom?smiles=...'</b></code>
	<br>

*/
public class db_badapple_prom_restletServer extends ServerResource
{  

  @Get  
  public Representation doGet(Representation entity)
  {  
    String str="";
    org.json.JSONObject jsonob = new JSONObject();

    //str+=("DEBUG: Resource URI  : " + getReference() + '\n' + "Root URI      : "  
    //        + getRootRef() + '\n' + "Routed part   : "  
    //        + getReference().getBaseRef() + '\n' + "Remaining part: "  
    //        + getReference().getRemainingPart()+"\n");

    org.restlet.Request request = getRequest();
    org.restlet.data.Method method = request.getMethod();
    org.restlet.data.Form form = request.getResourceRef().getQueryAsForm();
    String smiles=form.getFirstValue("smiles",true,null);
    String repr=form.getFirstValue("repr",true,"string");
    
    ArrayList<String> scaffold = new ArrayList<String>();
    ArrayList<Float> promiscuity = new ArrayList<Float>();
    ArrayList<Integer> in_drugs = new ArrayList<Integer>();
    ArrayList<Integer> scaffold_id = new ArrayList<Integer>();
    ArrayList<Integer> total_substances = new ArrayList<Integer>();
    ArrayList<Integer> active_substances = new ArrayList<Integer>();
    ArrayList<Integer> tested_assays = new ArrayList<Integer>();
    ArrayList<Integer> active_assays = new ArrayList<Integer>();
    ArrayList<Integer> tested_samples = new ArrayList<Integer>();
    ArrayList<Integer> active_samples = new ArrayList<Integer>();

    try {

      String DBHOST="krypton.health.unm.edu";
      String DBSCHEMA="carlsbad";
      String DBTABLE="SCAFFOLDS1";
      String DBPORT="1521";
      String DBID="KRDB";
      String DBUSER="webuser";
      String DBPW="foobar";

      DriverManager.registerDriver(new org.postgresql.Driver());
      Connection dbcon=DriverManager.getConnection("jdbc:postgresql://"+DBHOST+":"+DBPORT+"/"+DBID,DBUSER,DBPW);

      String sql=(
	" SELECT DISTINCT "+
	DBTABLE+".scaffold,"+
	DBTABLE+".promiscuity,"+
	DBTABLE+".in_drugs,"+
	DBTABLE+".scaffold_id,"+
	DBTABLE+".total_substances,"+
	DBTABLE+".active_substances,"+
	DBTABLE+".tested_assays,"+
	DBTABLE+".active_assays,"+
	DBTABLE+".tested_samples,"+
	DBTABLE+".active_samples,"+
	DBTABLE+".record"+
	" FROM "+DBSCHEMA+"."+DBTABLE+
	" WHERE jc_compare("+DBTABLE+".scaffold,'"+smiles+"','t:u')=1"+
	" ORDER BY LENGTH("+DBTABLE+".scaffold) DESC");

      Statement stmt=dbcon.createStatement();
      ResultSet rset=stmt.executeQuery(sql);

      if (rset!=null)
      {
        int n_scaf=0;
        while (rset.next())
        {
          ++n_scaf;
          Molecule mol_scaf=MolImporter.importMol(rset.getString(1),"mrv");
          String scafsma=mol_scaf.exportToFormat("smarts:");
          scaffold.add(scafsma);
          promiscuity.add(rset.getFloat(2));
          in_drugs.add(rset.getInt(3));
          scaffold_id.add(rset.getInt(4));
          total_substances.add(rset.getInt(5));
          active_substances.add(rset.getInt(6));
          tested_assays.add(rset.getInt(7));
          active_assays.add(rset.getInt(8));
          tested_samples.add(rset.getInt(9));
          active_samples.add(rset.getInt(10));

          str+=("scaffold["+n_scaf+"]: "+scafsma+"\n");
          str+=("promiscuity["+n_scaf+"]: "+rset.getFloat(2)+"\n");
          str+=("in_drugs["+n_scaf+"]: "+rset.getInt(3)+"\n");
          str+=("scaffold_id["+n_scaf+"]: "+rset.getInt(4)+"\n");
          str+=("total_substances["+n_scaf+"]: "+rset.getInt(5)+"\n");
          str+=("active_substances["+n_scaf+"]: "+rset.getInt(6)+"\n");
          str+=("tested_assays["+n_scaf+"]: "+rset.getInt(7)+"\n");
          str+=("active_assays["+n_scaf+"]: "+rset.getInt(8)+"\n");
          str+=("tested_samples["+n_scaf+"]: "+rset.getInt(9)+"\n");
          str+=("active_samples["+n_scaf+"]: "+rset.getInt(10)+"\n");
        }
        rset.getStatement().close();
        str+=("n_scaf: "+n_scaf+"\n");
        jsonob.put("n_scaf",n_scaf);
      }
      if (dbcon!=null) dbcon.close();

      //str+="DEBUG: db connection ok.\n";
      //jsonob.put("msg","DEBUG: db connection ok.");

      jsonob.put("scaffold",scaffold);
      jsonob.put("promiscuity",promiscuity);
      jsonob.put("in_drugs",in_drugs);
      jsonob.put("scaffold_id",scaffold_id);
      jsonob.put("total_substances",total_substances);
      jsonob.put("active_substances",active_substances);
      jsonob.put("tested_assays",tested_assays);
      jsonob.put("active_assays",active_assays);
      jsonob.put("tested_samples",tested_samples);
      jsonob.put("active_samples",active_samples);

      if (dbcon!=null) dbcon.close();
    }
    catch (SQLException e)
    {
      return new StringRepresentation("ERROR (SQLException):"+e.getMessage());
    }
    catch (chemaxon.formats.MolFormatException e) {
      return new StringRepresentation(e.getMessage());
    }
    catch (JSONException e) {
      return new StringRepresentation("ERROR (JSONException): "+e.getMessage());
    }
    catch (Exception e) {
      return new StringRepresentation("ERROR (Exception): "+e.getMessage());
    }
    if (repr.equalsIgnoreCase("json"))
      return new JsonRepresentation(jsonob);
    else
      return new StringRepresentation(str);
  }

  @Post
  public void doPost(Representation entity) throws ResourceException
  {
    org.restlet.Request request = getRequest();
    Form form = request.getResourceRef().getQueryAsForm();
    //for (Parameter parameter : form) {
    //  System.out.print("parameter " + parameter.getName());
    //  System.out.println("/" + parameter.getValue());
    //}
  }
}
