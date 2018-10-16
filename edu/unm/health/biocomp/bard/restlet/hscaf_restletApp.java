package edu.unm.health.biocomp.bard.restlet;

import java.io.*;
import java.util.*;

import org.restlet.*;
import org.restlet.resource.*;
import org.restlet.data.*;
import org.restlet.representation.Representation;
import org.restlet.routing.*;
import org.restlet.util.*;

/**	hscaf Application.  Invoked by Component.
	Accept smiles via url, or if absent, use test smiles.
	
	test:
	wget -q -O - 'http://localhost:8182/bard/hscaf/hscaf?smiles=NC(C)Cc1ccccc1'
*/
public class hscaf_restletApp extends Application
{  
  public hscaf_restletApp()
  {
    setName("RESTful molecular HierS scaffold analysis");
    setDescription("Example Restlet application, using ChemAxon JChem");
    setOwner("UNM Translational Informatics, Albuquerque, New Mexico");
    setAuthor("Jeremy J Yang");
  }

  @Override
  public Restlet createInboundRoot()
  {
    Router router = new Router(getContext());
    // router.attach("/bard/hscaf/hscaf", hscaf_restletServer.class, Router.MODE_FIRST_MATCH); //for test
    // Note that Component Router routes "/bard/hscaf" to this Application (Restlet Root URI).
    router.attach("/hscaf", hscaf_restletServer.class, Router.MODE_FIRST_MATCH); //for Tomcat
    router.attachDefault(notfound_restletServer.class);
    return router;
  }
}
