package edu.unm.health.biocomp.bard.restlet;

import java.io.*;
import java.util.*;

import org.restlet.*;
import org.restlet.resource.*;
import org.restlet.data.*;
import org.restlet.representation.Representation;
import org.restlet.routing.*;
import org.restlet.util.*;

/**	Component, for deployment as a Tomcat servlet.
	Accept smiles via url, or if absent, use test smiles.
	
	test:
	wget -q -O - 'http://localhost:8080/bard/calc/logp?smiles=NC(C)Cc1ccccc1'
	wget -q -O - 'http://localhost/tomcat/bard/calc/logp?smiles=NC(C)Cc1ccccc1'
	wget -q -O - 'http://localhost:8080/jjy/bard/hscaf/hscaf?smiles=COc1cc2c(ccnc2cc1)C(O)C4CC(CC3)C(C=C)CN34'
*/
public class bard_restletComponent extends Component
{  
  public bard_restletComponent()
	throws Exception
  {
    setName("Restlet Component for MLBD services");
    setDescription("Restlet Component for MLBD services");
    setOwner("UNM Translational Informatics, Albuquerque, New Mexico");
    setAuthor("Jeremy J Yang");

    getDefaultHost().attach("/bard/hscaf",new hscaf_restletApp());
    getDefaultHost().attach("/bard/calc",new calculator_restletApp());
    getDefaultHost().attach("/bard/db/hscaf",new db_hscaf_restletApp());
    getDefaultHost().attach("/bard/db/badapple",new db_badapple_restletApp());
    //getDefaultHost().attachDefault(new calculator_restletApp());
  }
}
