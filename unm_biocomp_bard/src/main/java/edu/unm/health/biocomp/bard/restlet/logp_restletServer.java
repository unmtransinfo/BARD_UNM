package edu.unm.health.biocomp.bard.restlet;

import java.io.*;
import java.util.*;
import chemaxon.formats.*;
import chemaxon.struc.*;
import chemaxon.util.MolHandler;
import chemaxon.sss.search.SearchException;

import chemaxon.marvin.plugin.CalculatorPlugin;
import chemaxon.marvin.plugin.PluginException;
import chemaxon.marvin.calculations.logPPlugin;

import org.restlet.*;
import org.restlet.resource.*;
import org.restlet.data.*;
import org.restlet.representation.*;
import org.restlet.util.*;

import org.json.*;
import org.restlet.ext.json.*;

/**	Simple logp calculator ServerResource.  Invoked by Application.
	Accept smiles via url, or if absent, use test smiles.
	Content negotiation via templated annotations as per recommendation from
	Jerome Louvel found online at restlet-discuss.

	curl -H "Accept: text/plain"  'http://localhost/tomcat/jjy/bard/calc/logp?smiles=NC(C)Cc1ccccc1'
	curl -H "Accept: application/json"  'http://localhost/tomcat/jjy/bard/calc/logp?smiles=NC(C)Cc1ccccc1'
*/
public class logp_restletServer extends ServerResource
{  
  org.restlet.Request request=null;
  org.restlet.data.Method method=null;
  String repr=null;
  org.restlet.data.Form form = null;

  //app-specific:
  String smiles=null;
  Molecule mol=null;
  logPPlugin plugin_logP=null;
  Float pH=7.0f;

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
      plugin_logP= new logPPlugin();
      plugin_logP.setUserTypes("logPTrue,logPMicro,logPNonionic,logDpI,increments");
      mol=MolImporter.importMol(smiles,"smiles:");
      plugin_logP.setMolecule(mol);
      plugin_logP.run();
    }
    catch (chemaxon.marvin.plugin.PluginException e) {
      throw new ResourceException(e);
    }
    catch (chemaxon.formats.MolFormatException e) {
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

    //Map<String,Object> request_attrs = getRequestAttributes();
    //str+=("DEBUG: request_attrs.size()="+request_attrs.size()+"\n");
    //for (String key:request_attrs.keySet())
    //  str+=("DEBUG: request_attrs.keySet(\""+key+"\") = "+ request_attrs.get(key)+"\n");

    str+=(""+"date: "+(new Date())+"\n");
    str+=("smiles: "+smiles+"\n");
    str+=(String.format("logP: %.2f\n",plugin_logP.getlogPMicro()));       //logP of input
    str+=(String.format("Nonionic logP : %.2f\n",plugin_logP.getlogPNonionic()));   //logP of neutral
    str+=(String.format("True logP : %.2f\n",plugin_logP.getlogPTrue()));       //typical from above

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
      jsonob.put("logP",plugin_logP.getlogPMicro());       //logP of input
      jsonob.put("Nonionic logP",plugin_logP.getlogPNonionic());   //logP of neutral
      jsonob.put("True logP",plugin_logP.getlogPTrue());       //typical from above
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

    //org.restlet.Request request = getRequest();
    //Form form = request.getResourceRef().getQueryAsForm();
    //for (Parameter parameter : form) {
    //  System.out.print("parameter " + parameter.getName());
    //  System.out.println("/" + parameter.getValue());
    //}
  }
  @Post("json")
  public void doPostJson(Representation entity,Variant variant) throws ResourceException
  {
    doGetJson(variant);
  }
}
