package edu.unm.health.biocomp.bard.util;

import java.io.*;
import java.util.*;
import java.text.DateFormat;
import java.util.regex.Pattern;
import java.net.*; //HttpURLConnection,MalformedURLException,URI,URISyntaxException,URL,URLConnection,URLEncoder
//import javax.ws.rs.*;
//import javax.ws.rs.core.*;

import org.json.*;

import edu.unm.health.biocomp.util.*;

/**
	Static methods for BARD REST API requests.

	@author	Jeremy Yang
*/
public class bard_api_utils
{
  private bard_api_utils() { } //disallow default constructor

  public static String BARD_API_HOST="bard.nih.gov";
  public static String BARD_API_BASEPATH="/api";
  public static String BARD_API_VERSION="latest";

  /**	Construct base URI for API queries.  If null specified for any param, default used.
	@param host fully resolved host name
	@param basepath path to API (e.g. "/api")
	@param version API version (e.g. "latest", "straw")
  */
  public static String BaseURI(String host, String basepath, String version)
  {
    host=(host!=null)?host:BARD_API_HOST;
    version=(version!=null)?version:BARD_API_VERSION;
    basepath=(basepath!=null)?basepath:BARD_API_BASEPATH;
    return ("http://"+host+basepath+"/"+version);
  }

  public static void GetURI(String uri,HashMap<String,String> headers,JSONObject jsonob,int verbose)
	throws Exception
  {
    RequestURI(uri,headers,null,jsonob,verbose);
  }
  public static void PostURI(String uri,HashMap<String,String> headers,HashMap<String,String> data,JSONObject jsonob,int verbose)
	throws Exception
  {
    RequestURI(uri,headers,data,jsonob,verbose);
  }
  public static void PostURI_JSONArray(String uri,HashMap<String,String> headers,HashMap<String,String> data,JSONArray jsonarray,int verbose)
  {
    RequestURI_JSONArray(uri,headers,data,jsonarray,verbose);
  }
  public static void RequestURI_JSONArray(String uri,HashMap<String,String> headers,HashMap<String,String> data,JSONArray jsonarray,int verbose)
  {
    //JJY to do...
  }

  /**	GET or POST request to BARD API; parse JSON and return JSONObject .
  */
  public static void RequestURI(String uri,HashMap<String,String> headers,HashMap<String,String> data,JSONObject jsonob,int verbose)
	throws Exception
	//throws URISyntaxException,MalformedURLException,ProtocolException,UnsupportedEncodingException,IOException,JSONException
  {
    StringBuilder sb = new StringBuilder();
    URL url = (new URI(uri)).toURL();
    HttpURLConnection hcon = (HttpURLConnection) url.openConnection();
    if (headers!=null)
    {
      for (String key: headers.keySet())
        hcon.setRequestProperty(key,headers.get(key));
    }
    OutputStreamWriter oswriter=null;
    if (data!=null) //POST: construct & send body data.
    {
      hcon.setRequestMethod("POST");
      hcon.setDoOutput(true);
      String body="";
      int j=0;
      for (String key: data.keySet())
      {
        if (j>0) body+="&";
        body+=(URLEncoder.encode(key,"UTF-8")+"="+URLEncoder.encode(data.get(key),"UTF-8"));
        j+=1;
      }
      oswriter = new OutputStreamWriter(hcon.getOutputStream());
      oswriter.write(body);
      oswriter.flush();
    }
    else //GET is default
    {
      hcon.setRequestMethod("GET");
    }
    if (verbose>2)
    {
      System.err.println("DEBUG: URL = "+hcon.getURL());
      System.err.println("DEBUG: RequestMethod = "+hcon.getRequestMethod());
      if (headers!=null)
      for (String key: headers.keySet())
        System.err.println("DEBUG: request header key = "+key+", val = "+headers.get(key));
    }
    // hcon.connect(); // invoked implicitly
    int resp_code=hcon.getResponseCode();
    String resp_msg=hcon.getResponseMessage();
    if (verbose>2)
    {
      System.err.println("DEBUG: ResponseCode = "+hcon.getResponseCode());
      System.err.println("DEBUG: ResponseMessage = "+hcon.getResponseMessage());
      for (String key: hcon.getHeaderFields().keySet())
        System.err.println("DEBUG: response header key = "+key+", val = "+hcon.getHeaderField(key));
      System.err.println("DEBUG: ContentType = "+hcon.getContentType());
      System.err.println("DEBUG: ContentEncoding = "+hcon.getContentEncoding());
      System.err.println("DEBUG: ContentLength = "+hcon.getContentLength());
    }
    if (resp_code==200)
    {
      BufferedReader reader = new BufferedReader(new InputStreamReader(hcon.getInputStream()));
      String line;
      while ((line=reader.readLine())!=null) sb.append(line+"\n");
      reader.close();
      String str = sb.toString();
      if (str.startsWith("{") || str.matches("^\\s*\\{.*$")) //Looks like JSONObject.
      {
        //System.err.println("DEBUG: Looks like JSON: "+str);
        jsonob = new JSONObject(str);
      }
      else if (str.startsWith("[") || str.matches("^\\s*\\[.*$")) //Looks like JSONArray.
      {
        //System.err.println("DEBUG: Looks like JSON: "+str);
        JSONArray jsonarray = new JSONArray(str);
        if (jsonarray.length()>0) jsonob = (JSONObject) jsonarray.get(0); //Use 1st object only!
      }
      else //Handle as plain string.
      {
        //System.err.println("DEBUG: Looks like plain string: "+str);
        str=str.replaceAll("\\n","\\\\n");
        jsonob = new JSONObject("{\"value\":\""+str+"\"}");
      }
      //System.err.println("DEBUG: jsonob: "+jsonob.toString(2));
    }
    else
    {
      System.err.println("HTTP response ["+resp_code+"]: "+resp_msg);
    }
    if (oswriter!=null) oswriter.close();
  }
  /**	Lookup data for given CID.
  */
  public static JSONObject Cid2Data(String baseuri,long id,int verbose)
	throws Exception
  {
    JSONObject jsonob = new JSONObject();
    GetURI(baseuri+"/compounds/"+id,null,jsonob,verbose);
    //System.err.println("DEBUG: jsonob: "+jsonob.toString(2));
    return jsonob;
  }
  /**	Lookup SMILES for given CID.
  */
  public static String Cid2Smiles(String baseuri,long id,int verbose)
	throws Exception
  {
    JSONObject jsonob=Cid2Data(baseuri,id,verbose);
    String smiles=jsonob.getString("smiles");
    return smiles;
  }

  public static JSONArray Cids2Data(String baseuri,ArrayList<Long> ids,int verbose)
  {
    HashMap<String,String> headers = new HashMap<String,String>();
    HashMap<String,String> data = new HashMap<String,String>();
    JSONArray jsonarray = new JSONArray();
    PostURI_JSONArray(baseuri+"/compounds",headers,data,jsonarray,verbose);

    //JJY to do....

    return jsonarray;
  }

  public static int ProjectCount(String baseuri,int verbose)
	throws Exception
  {
    int n_proj=0;
    JSONObject jsonob = new JSONObject();
    GetURI(baseuri+"/projects/_count",null,jsonob,verbose);
    n_proj=Integer.parseInt(jsonob.getString("value"));
    return n_proj;
  }

  public static boolean Ping(String baseuri,int verbose)
  {
    JSONObject jsonob = new JSONObject();
    try { GetURI(baseuri+"/projects/_count",null,jsonob,verbose); }
    catch (Exception e) { return false; }
    return true;
  }

  /**	.
  */
  public static int GetProjects(String baseuri,File fout,int verbose)
	throws Exception
  {
    PrintWriter fout_writer=new PrintWriter(new BufferedWriter(new FileWriter(fout,false))); //overwrite
    List<String> tags=Arrays.asList("projectId","name","source","category","type","classification","experimentCount","eids","targets");
    for (int j=0;j<tags.size();++j)
    {
      fout_writer.printf(((j>0)?",":"")+"\""+tags.get(j)+"\"");
    }
    fout_writer.printf("\n");

    int n_out=0;
    int n_err=0;
    int nchunk=100;

    String link=("/projects?expand=true&top="+nchunk);
    while (true)
    {
      JSONObject jsonob = new JSONObject();
      GetURI(baseuri+link,null,jsonob,verbose);
      if (jsonob==null)
      {
        ++n_err;
        break; // ERROR
      }
      //System.err.println("DEBUG: jsonob: "+jsonob.toString(2));
      JSONArray projects=jsonob.getJSONArray("collection");
      if (projects.length()==0) break; // END OF PIDs
      for (int i=0;i<projects.length();++i)
      {
        ++n_out;
        JSONObject project=projects.getJSONObject(i);
        ArrayList<String> vals = new ArrayList<String>();
        for (String tag: tags)
        {
          if (tag.equals("targets"))
          {
            JSONArray targets=project.getJSONArray("targets");
            if (targets.length()>0)
            {
//              List<Object> targeturis = new ArrayList<Object>();
//              for (int j=0;j<targets.length();++j)
//              {
//                targeturis.add(targets.getJSONObject(j).getString("resourcePath"));
//              }
//              String targeturis_str=targeturis.toArray().toString();
//              System.err.println("DEBUG: targeturis_str: "+targeturis_str);
//              vals.add(targeturis_str);

              String val="[";
              for (int j=0;j<targets.length();++j)
              {
                val+=(((j>0)?",":"")+"\""+targets.getJSONObject(j).getString("resourcePath")+"\"");
              }
              val+="]";
              vals.add(ToStringForCSV(val,0));
            }
            else
              vals.add("");
          }
          else
          {
            vals.add(ToStringForCSV(project.get(tag),80));
          }
        }
        for (int j=0;j<vals.size();++j)
        {
          fout_writer.printf(((j>0)?",":"")+vals.get(j));
          ++n_out;
        }
        fout_writer.printf("\n");
      }
      link=jsonob.getString("link");
      if (link==null && link.isEmpty()) break; // END of PIDs
    }
    if (verbose>0)
    {
      System.err.println("GetProjects: n_out: "+n_out);
      System.err.println("GetProjects: n_err: "+n_err);
    }
    return n_out;
  }

  /**	.
  */
  private static String ToStringForCSV(String val,int maxlen)
  {
    if (maxlen>0 && val.length()>maxlen) { val=val.substring(0,maxlen-1)+"..."; }
    val=val.replaceAll("\\n"," ");
    val=val.replaceAll("\"","\\\\\"");
    val=("\""+val+"\"");
    return val;
  }
  private static String ToStringForCSV(List<Object> val,int maxlen)
  {
    String valstr=null;
    if (val.size()==0) return "";
    valstr=val.toArray().toString();
    valstr=valstr.replaceAll("\\s","");
    valstr=valstr.replaceAll("\"","\\\"");
    valstr=("\""+valstr+"\"");
    return valstr;
  }
  private static String ToStringForCSV(Object val,int maxlen)
  {
    String valstr=null;
    try { valstr=val.toString(); }
    catch (Exception e) { val=null; }
    return "\""+valstr+"\"";
  }
}
