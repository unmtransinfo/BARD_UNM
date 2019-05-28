package edu.unm.health.biocomp.bard.restlet;

import java.io.*;
import java.util.*;
import chemaxon.formats.*;
import chemaxon.struc.*;
import chemaxon.util.MolHandler;
import chemaxon.sss.search.SearchException;

import chemaxon.marvin.plugin.CalculatorPlugin;
import chemaxon.marvin.plugin.PluginException;
import chemaxon.marvin.calculations.logDPlugin;


import org.restlet.*;
import org.restlet.resource.*;
import org.restlet.data.*;
import org.restlet.representation.*;
import org.restlet.util.*;

import org.json.*;
import org.restlet.ext.json.*;

/**	Simple logd calculator ServerResource. Invoked by Application.
	Accept smiles via url.
	
*/
public class logd_restletServer extends ServerResource
{
  org.restlet.Request request=null;
  org.restlet.data.Method method=null;
  String repr=null;
  org.restlet.data.Form form = null;

  //app-specific:
  String smiles=null;
  Molecule mol=null;
  logDPlugin plugin_logD=null;

  Float pH=7.0f;
  Float pHLower=0.0f;
  Float pHUpper=0.0f;
  Float pHStep=0.0f;
  double[] pHs;
  double[] logDs;
  double logD;

  @Override
  protected void doInit() throws ResourceException
  {
    request=getRequest();
    method=request.getMethod();
    form=request.getResourceRef().getQueryAsForm();
    repr=form.getFirstValue("repr",true,"string");

    //app-specific:
    smiles=form.getFirstValue("smiles",true,null);

    try {pH=Float.parseFloat(form.getFirst("pH",true).getValue());} catch (Exception e) {};
    try {pHLower=Float.parseFloat(form.getFirst("pHLower",true).getValue());} catch (Exception e) {};
    try {pHUpper=Float.parseFloat(form.getFirst("pHUpper",true).getValue());} catch (Exception e) {};
    try {pHStep=Float.parseFloat(form.getFirst("pHStep",true).getValue());} catch (Exception e) {};

    try
    {
      mol=MolImporter.importMol(smiles,"smiles:");
      plugin_logD= new logDPlugin();
      plugin_logD.setpH(pH);

      if (pHLower!=0.0f && pHUpper!=0.0f && pHStep!=0.0f)
      {
        plugin_logD.setpHLower(pHLower);
        plugin_logD.setpHUpper(pHUpper);
        plugin_logD.setpHStep(pHStep);
      }
      else
      {
        plugin_logD.setpH(pH);
      }
      plugin_logD.setCloridIonConcentration(0.2);
      plugin_logD.setNaKIonConcentration(0.2);
      plugin_logD.setMolecule(mol);
      plugin_logD.run();
      if (pHLower!=0.0f && pHUpper!=0.0f && pHStep!=0.0f)
      {
        pHs=plugin_logD.getpHs();
        logDs=plugin_logD.getlogDs();
      }
      else
      {
        logD=plugin_logD.getlogD();
      }
    }
    catch (chemaxon.marvin.plugin.PluginException e) {
      throw new ResourceException(e);
    }
    catch (chemaxon.formats.MolFormatException e) {
      throw new ResourceException(e);
    }
    catch (Exception e) {
      throw new ResourceException(e);
    }
  }

  @Get("txt")
  public Representation doGetTxt(Variant variant)
  {
    String str="";
    str+=("DEBUG: Resource URI  : " + getReference() + '\n' + "Root URI      : "  
            + getRootRef() + '\n' + "Routed part   : "  
            + getReference().getBaseRef() + '\n' + "Remaining part: "  
            + getReference().getRemainingPart()+"\n");

    str+=(""+"date: "+(new Date())+"\n");
    str+=("smiles: "+smiles+"\n");
    str+=(String.format("pH: %.2f\n",pH));
    str+=(String.format("pHLower: %.2f\n",pHLower));
    str+=(String.format("pHUpper: %.2f\n",pHUpper));
    str+=(String.format("pHStep: %.2f\n",pHStep));

    if (pHLower!=0.0f && pHUpper!=0.0f && pHStep!=0.0f)
    {
      for (int i=0;i< pHs.length;++i)
        str+=(String.format("logD: %.2f @pH=%.2f\n",logDs[i],pHs[i]));
    }
    else
      str+=(String.format("logD: %.2f\n",logD));
    return new StringRepresentation(str);
  }

  @Get("json")
  public Representation doGetJson(Variant variant)
  {
    org.json.JSONObject jsonob = new JSONObject();

    try
    {
      jsonob.put("smiles",smiles);
      jsonob.put("date",(new Date()).toString());
      jsonob.put("pH",pH);
      jsonob.put("pHLower",pHLower);
      jsonob.put("pHUpper",pHUpper);
      jsonob.put("pHStep",pHStep);

      if (pHLower!=0.0f && pHUpper!=0.0f && pHStep!=0.0f)
      {
        jsonob.put("pHs",pHs);
        jsonob.put("logDs",logDs);
      }
      else
      {
        jsonob.put("logD",logD);
      }
    }
    catch (JSONException e) {
      throw new ResourceException(e);
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
