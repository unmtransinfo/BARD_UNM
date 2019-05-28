package edu.unm.health.biocomp.bard.restlet;

import java.io.*;
import java.util.*;

import org.restlet.*;
import org.restlet.resource.*;
import org.restlet.data.*;
import org.restlet.representation.Representation;
import org.restlet.routing.*;
import org.restlet.util.*;

/**	HierS scaffold db Application.  Invoked by Component.
	<br>
	Test:
	<br>
	&nbsp; <code><b>wget -q -O - 'http://localhost:8080/bard/db/hscaf/smiles?id=17'</b></code>
*/
public class db_hscaf_restletApp extends Application
{  
  public db_hscaf_restletApp()
  {
    setName("Restlet db Application");
    setDescription("Restlet db Application");
    setOwner("UNM Translational Informatics, Albuquerque, New Mexico");
    setAuthor("Jeremy J Yang");
  }

  @Override
  public Restlet createInboundRoot()
  {
    Router router = new Router(getContext());
    // Note that Component Router routes "/bard/db/hscaf" to this Application (Restlet Root URI).
    router.attach("/id", db_hscaf_id_restletServer.class, Router.MODE_FIRST_MATCH);
    router.attach("/smiles", db_hscaf_smiles_restletServer.class, Router.MODE_FIRST_MATCH);
    router.attachDefault(notfound_restletServer.class);
    return router;
  }
}
