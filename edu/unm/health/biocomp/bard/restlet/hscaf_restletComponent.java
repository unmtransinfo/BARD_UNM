package edu.unm.health.biocomp.bard.restlet;

import java.io.*;
import java.util.*;

import org.restlet.*;
import org.restlet.resource.*;
import org.restlet.data.*;
import org.restlet.representation.Representation;
import org.restlet.routing.*;
import org.restlet.util.*;

/**	hscaf component, for deployment as a Tomcat servlet.
	Accept smiles via url, or if absent, use test smiles.
	
	test:
	wget -q -O - 'http://localhost:8080/bard/hscaf/hscaf?smiles=NC(C)Cc1ccccc1'
*/
public class hscaf_restletComponent extends Component
{  
  public hscaf_restletComponent()
	throws Exception
  {
    setName("RESTful molecular HierS scaffold component");
    setDescription("Example Restlet Component, using ChemAxon JChem");
    setOwner("UNM Translational Informatics, Albuquerque, New Mexico");
    setAuthor("Jeremy J Yang");

    getDefaultHost().attachDefault(new hscaf_restletApp());
  }
}
