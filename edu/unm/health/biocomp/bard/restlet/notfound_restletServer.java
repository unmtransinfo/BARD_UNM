package edu.unm.health.biocomp.bard.restlet;

import java.io.*;
import java.util.*;

import org.restlet.*;
import org.restlet.resource.*;
import org.restlet.data.*;
import org.restlet.representation.Representation;
import org.restlet.util.*;

/**	
	test:
*/
public class notfound_restletServer extends ServerResource
{  
  @Get  
  public String doGet(Representation entity)
  {  
    String str="";
    str+=("DEBUG: NOT FOUND\n");
    str+=("DEBUG: Resource URI  : " + getReference() + '\n' + "Root URI      : "  
            + getRootRef() + '\n' + "Routed part   : "  
            + getReference().getBaseRef() + '\n' + "Remaining part: "  
            + getReference().getRemainingPart()+"\n");
    return (str);
  }
}
