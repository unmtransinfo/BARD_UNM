package edu.unm.health.biocomp.bard.restlet;

import java.io.*;
import java.util.*;
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
import org.restlet.ext.xml.*;
import org.xml.sax.SAXException;

import edu.unm.health.biocomp.hscaf.*;

/**	Simple hscaf ServerResource.  Invoked by Application.
	Accept smiles via url.
	
	Test:
	  wget -q -O - 'http://localhost:8080/jjy/bard/hscaf/hscaf?smiles=COc1cc2c(ccnc2cc1)C(O)C4CC(CC3)C(C=C)CN34' 
	  curl -H "Accept: text/xml" 'http://localhost:8080/jjy/bard/hscaf/hscaf?smiles=COc1cc2c(ccnc2cc1)C(O)C4CC(CC3)C(C=C)CN34'

*/
public class hscaf_restletServer extends ServerResource
{
  org.restlet.Request request=null;
  org.restlet.data.Method method=null;
  String repr=null;
  org.restlet.data.Form form = null;

  //app-specific:
  String smiles=null;
  Molecule mol=null;
  String stereo=null;
  String keep_nitro_attachments=null;
  Integer n_frag=null;
  Integer n_ring=null;
  ScaffoldSet scafset=null;
  ScaffoldTree scaftree=null;
  Integer n_scaf=null;
  Integer n_link=null;
  Integer n_side=null;
  ArrayList<Long> scaflist=null;
  ArrayList<String> scafsmis=null;

  @Override
  protected void doInit() throws ResourceException
  {
    request=getRequest();
    method=request.getMethod();
    form=request.getResourceRef().getQueryAsForm();
    repr=form.getFirstValue("repr",true,"string");

    //app-specific:
    smiles=form.getFirstValue("smiles",true,null);
    stereo=form.getFirstValue("stereo",true,"");
    keep_nitro_attachments=form.getFirstValue("keep_nitro_attachments",true,"");

    try
    {
      mol=MolImporter.importMol(smiles,"smiles:");
      n_frag = mol.getFragCount();
      if (n_frag>1)
        mol=hier_scaffolds_utils.LargestPart(mol);
      n_ring=hier_scaffolds_utils.RawRingsystemCount(mol);
      scafset = new ScaffoldSet("Scaffold Set in Restlet Component");

      scaftree = new ScaffoldTree(mol,
	stereo.equalsIgnoreCase("true"),
	keep_nitro_attachments.equalsIgnoreCase("true"),
	scafset); //scafIDs assigned.
      n_scaf=scaftree.getScaffoldCount();
      n_link=scaftree.getLinkerCount();
      n_side=scaftree.getSidechainCount();
      scaflist = new ArrayList<Long>(); //scafIDs for this mol
      scafsmis = new ArrayList<String>(); //scafsmis for this mol
      for (Scaffold scaf: scaftree.getScaffolds())
      {
        scaflist.add(scaf.getID());
        scafsmis.add(scaf.getCansmi());
      }
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
    str+=("DEBUG: Resource URI  : "+getReference()+'\n'
         +"Root URI      : "+getRootRef()+'\n'
         +"Routed part   : "+getReference().getBaseRef()+'\n'
         +"Remaining part: "+getReference().getRemainingPart()+"\n"
         +"Variant.getMediaType() : "+variant.getMediaType()+"\n");

    str+=("n_frag: "+n_frag+"\n");
    str+=("n_ring: "+n_ring+"\n");
    str+=("n_scaf: "+n_scaf+"\n");
    str+=("n_link: "+n_link+"\n");
    str+=("n_side: "+n_side+"\n");
      
    for (Scaffold scaf: scaftree.getScaffolds())
      str+=("\t"+scaf.getID()+": "+scaf.getCansmi()+"\n");
    str+=("scaftree: "+scaftree.toString()+"\n");

    return new StringRepresentation(str);
  }

  @Get("json")
  public Representation doGetJson(Variant variant)
  {
    org.json.JSONObject jsonob = new JSONObject();

    try
    {
      jsonob.put("n_frag",n_frag);
      jsonob.put("n_ring",n_ring);
      jsonob.put("n_scaf",n_scaf);
      jsonob.put("n_link",n_link);
      jsonob.put("n_side",n_side);
      jsonob.put("scafsmis",scafsmis);
      jsonob.put("scaftree",scaftree.toString());
    }
    catch (JSONException e) {
      throw new ResourceException(e);
    }
    return new JsonRepresentation(jsonob);
  }

  @Get("xml")
  public Representation doGetXml(Variant variant)
  {
    return new SaxRepresentation() {
      public void write(XmlWriter xmlwriter)
      {
        try
        {
          xmlwriter.setDataFormat(true); //newlines, indents
          xmlwriter.setIndentStep(2);
          xmlwriter.startDocument();
          xmlwriter.startElement("hscaf");
          xmlwriter.dataElement("molsmi",smiles);
          xmlwriter.dataElement("n_frag",""+n_frag);
          xmlwriter.dataElement("n_ring",""+n_ring);
          xmlwriter.dataElement("n_scaf",""+n_scaf);
          xmlwriter.dataElement("n_link",""+n_link);
          xmlwriter.dataElement("n_side",""+n_side);
          xmlwriter.dataElement("scafsmis",""+scafsmis);
          xmlwriter.dataElement("scaftree",scaftree.toString());
          xmlwriter.endElement("hscaf");
          xmlwriter.endDocument();
        }
        catch (SAXException e) {
          throw new ResourceException(e);
        }
      };
    };
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
  @Post("xml")
  public void doPostXml(Representation entity,Variant variant) throws ResourceException
  {
    doGetXml(variant);
  }
}
