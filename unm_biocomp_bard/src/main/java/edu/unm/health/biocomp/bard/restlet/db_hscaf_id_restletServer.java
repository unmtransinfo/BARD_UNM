package edu.unm.health.biocomp.bard.restlet;

import java.io.*;
import java.util.*;

import java.sql.*;

//import org.postgresql.Driver;

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

import edu.unm.health.biocomp.db.pg_utils;

/**	Simple db ServerResource.  Invoked by Application.
	Given smiles, lookup ID.
	<br>
	Test:
	<br>
	  &nbsp; <code><b>wget -q -O - 'http://localhost:8080/jjy/bard/db/hscaf/id?smiles=N1CCCC1'</b></code>
*/
public class db_hscaf_id_restletServer extends ServerResource
{  

  org.restlet.Request request=null;
  org.restlet.data.Method method=null;
  String repr=null;
  org.restlet.data.Form form = null;

  //app-specific:
  String smiles=null;
  Molecule mol=null;

  String DBHOST="localhost";
  Integer DBPORT=5432;
  String DBSCHEMA="mba";
  String DBID="openchord";
  String DBUSR="www";
  String DBPW="foobar";
  Connection dbcon=null;
  ResultSet rset=null;


  @Override
  protected void doInit() throws ResourceException
  {
    request=getRequest();
    method=request.getMethod();
    form=request.getResourceRef().getQueryAsForm();
    repr=form.getFirstValue("repr",true,"string");

    //app-specific:
    smiles=form.getFirstValue("smiles",true,null);

    try
    {
      dbcon=pg_utils.DBConnect(DBHOST,DBPORT,DBID,DBUSR,DBPW);
      String sql=("SELECT id,ncpd_total,ncpd_tested,ncpd_active,nass_tested,nass_active,nsam_tested,nsam_active FROM "+DBSCHEMA+".scaffolds WHERE scafsmi=openbabel.cansmiles('"+smiles+"')");
      //sql=("SELECT cid,nass_tested,nass_active,nsam_tested,nsam_active FROM "+DBSCHEMA+".compounds WHERE cansmi=openbabel.cansmiles('"+smiles+"')");
      rset=pg_utils.ExecuteSql(dbcon,sql);
    }
    catch (SQLException e) {
      throw new ResourceException(e);
    }
  }

  @Override
  protected void doRelease() throws ResourceException
  {
    try
    {
      if (rset!=null) rset.getStatement().close();
      if (dbcon!=null) dbcon.close();
    }
    catch (SQLException e) {
      throw new ResourceException(e);
    }
  }

  @Get("txt")
  public Representation doGetTxt(Variant variant)
  {
    String str="";
    str+=("DEBUG: Resource URI  : "+getReference()+'\n'
            +"Root URI      : "+getRootRef()+'\n'
            +"Routed part   : "+getReference().getBaseRef()+'\n'
            +"Remaining part: "+getReference().getRemainingPart()+"\n"
            +"Variant.getMediaType() : "+variant.getMediaType()+"\n");

    try {

      str+=("DB: "+DBSCHEMA+":"+DBID+"@"+DBHOST+"\n");
      str+=pg_utils.ServerStatusTxt(dbcon)+"\n";

      if (rset!=null)
      {
        // If SMILES not found we return... not found 404 code?  How?
        if (rset.next())
        {
          str+=("scafID: "+rset.getInt(1)+"\n");
          str+=("ncpd_total: "+rset.getInt(2)+"\n");
          str+=("ncpd_tested: "+rset.getInt(3)+"\n");
          str+=("ncpd_active: "+rset.getInt(4)+"\n");
          str+=("nass_tested: "+rset.getInt(5)+"\n");
          str+=("nass_active: "+rset.getInt(6)+"\n");
          str+=("nsam_tested: "+rset.getInt(7)+"\n");
          str+=("nsam_active: "+rset.getInt(8)+"\n");
        }
      }
      else
      {
        str+=("scaf not found.\n");
      }

    }
    catch (SQLException e)
    {
      return new StringRepresentation(e.getMessage());
    }
    catch (Exception e) {
      return new StringRepresentation(e.getMessage());
    }
    return new StringRepresentation(str);
  }

  @Get("json")
  public Representation doGetJson(Variant variant)
  {
    org.json.JSONObject jsonob = new JSONObject();

    Integer scafid=null;
    
    try {

      if (rset!=null)
      {
        // If SMILES not found we return... not found 404 code?  How?
        if (rset.next())
        {
          jsonob.put("scafID",rset.getInt(1));
          jsonob.put("ncpd_total",rset.getInt(2));
          jsonob.put("ncpd_tested",rset.getInt(3));
          jsonob.put("ncpd_active",rset.getInt(4));
          jsonob.put("nass_tested",rset.getInt(5));
          jsonob.put("nass_active",rset.getInt(6));
          jsonob.put("nsam_tested",rset.getInt(7));
          jsonob.put("nsam_active",rset.getInt(8));
        }
      }
      else
      {
        jsonob.put("DEBUG","scaf not found");
      }
    }
    catch (SQLException e)
    {
      return new StringRepresentation(e.getMessage());
    }
    catch (JSONException e) {
      return new StringRepresentation(e.getMessage());
    }
    catch (Exception e) {
      return new StringRepresentation(e.getMessage());
    }
    return new JsonRepresentation(jsonob);
  }

  @Post("txt")
  public void doPostTxt(Representation entity,Variant variant) throws ResourceException
  {
    doGetTxt(variant);
  }

  @Post("json")
  public void doPostJson(Representation entity,Variant variant) throws ResourceException
  {
    doGetJson(variant);
  }
}
