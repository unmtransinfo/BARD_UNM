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

/**	Simple db ServerResource.  Invoked by Application.
	Given ID, lookup smiles.
	<br>
	Test:
	<br>
	 &nbsp; <code>wget -q -O - 'http://localhost:8080/jjy/bard/db/hscaf/smiles?id=17'</code>
	<pre>
SELECT DISTINCT
	SCAFFOLDS1.scaffold,
	SCAFFOLDS1.promiscuity,
	SCAFFOLDS1.in_drugs,
	SCAFFOLDS1.scaffold_id,
	SCAFFOLDS1.total_substances,
	SCAFFOLDS1.active_substances,
	SCAFFOLDS1.tested_assays,
	SCAFFOLDS1.active_assays,
	SCAFFOLDS1.tested_samples,
	SCAFFOLDS1.active_samples,
	SCAFFOLDS1.record
FROM
	carlsbad.SCAFFOLDS1
WHERE
	jc_compare(scaffold,'c1ccc2c(c1)c(=O)n(s2)c3ccccc3C(=O)N4CCCC4','t:u')=1
ORDER BY LENGTH(scaffold) DESC
	</pre>
*/
public class db_hscaf_smiles_restletServer extends ServerResource
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
    String id=form.getFirstValue("id",true,null);
    String scafsmi="";
    String repr=form.getFirstValue("repr",true,"string");

    // Note that this ServerResource obtains data via JDBC.  However, another possibility would
    // be to use a Restlet ClientResource and obtain data via REST server.  A client which 
    // accesses this server might be:
    //   ClientResource dbClient = new ClientResource("http://localhost:8080/db/smiles?"+smiles);
    //   Representation repr = dbClient.get();
    //   String txt = repr.getText();    

    try {

      int id_int = Integer.parseInt(id);

      /// Test connection for status check.

      String DBHOST="krypton.health.unm.edu";
      String DBSCHEMA="carlsbad";
      String DBTABLE="SCAFFOLDS1";
      String DBPORT="1521";
      String DBID="KRDB";
      String DBUSER="webuser";
      String DBPW="foobar";

      DriverManager.registerDriver(new oracle.jdbc.driver.OracleDriver());
      Connection dbcon=DriverManager.getConnection("jdbc:oracle:thin:@"+DBHOST+":"+DBPORT+":"+DBID,DBUSER,DBPW);

      String sql="SELECT "+DBTABLE+".scaffold FROM "+DBSCHEMA+"."+DBTABLE+" WHERE "+DBTABLE+".scaffold_id="+id_int;
      Statement stmt=dbcon.createStatement();
      ResultSet rset=stmt.executeQuery(sql);

      if (rset!=null)
      {
        rset.next();
        String scafmrv=rset.getString(1); //MRV-format
        rset.getStatement().close();
        Molecule mol_scaf=MolImporter.importMol(scafmrv,"mrv");
        scafsmi=mol_scaf.exportToFormat("smarts:");
      }
      if (dbcon!=null) dbcon.close();

      //str+="DEBUG: db connection ok.\n";
      //jsonob.put("msg","DEBUG: db connection ok.");

      str+=("scafsmi: "+scafsmi+"\n");
      jsonob.put("scafsmi",scafsmi);

    }
    catch (SQLException e)
    {
      return new StringRepresentation(e.getMessage());
    }
    catch (chemaxon.formats.MolFormatException e) {
      return new StringRepresentation(e.getMessage());
    }
    catch (JSONException e) {
      return new StringRepresentation(e.getMessage());
    }
    catch (Exception e) {
      return new StringRepresentation(e.getMessage());
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
