package edu.unm.health.biocomp.bard.restlet;

import java.io.*;
import java.util.*;

import org.restlet.*;
import org.restlet.resource.*;
import org.restlet.data.*;
import org.restlet.representation.Representation;
import org.restlet.routing.*;
import org.restlet.util.*;

/**	Component for standalone testing.
	
	test:
	  wget -q -O - 'http://localhost:8111/bard/calc/logp?smiles=NC(C)Cc1ccccc1'
*/
public class bard_restletComponentTest extends Component
{  
  public static void main(String[] args) throws Exception
  {
    new bard_restletComponentTest().start();
  }

  public bard_restletComponentTest()
	throws Exception
  {
    setName("Example Restlet standalong Component");
    setDescription("Example Restlet standalong Component");
    setOwner("UNM Translational Informatics, Albuquerque, New Mexico");
    setAuthor("Jeremy J Yang");

    getServers().add(Protocol.HTTP, 8111); //for standalone daemon deployment

    getDefaultHost().attach("/bard/hscaf",new hscaf_restletApp());
    getDefaultHost().attach("/bard/calc",new calculator_restletApp());
    getDefaultHost().attach("/bard/db/hscaf",new db_hscaf_restletApp());

    getDefaultHost().attachDefault(new calculator_restletApp());
  }
}
