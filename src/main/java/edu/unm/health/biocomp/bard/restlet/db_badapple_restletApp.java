package edu.unm.health.biocomp.bard.restlet;

import java.io.*;
import java.util.*;

import org.restlet.*;
import org.restlet.resource.*;
import org.restlet.data.*;
import org.restlet.representation.Representation;
import org.restlet.routing.*;
import org.restlet.util.*;

/**	BADAPPLE db promiscuity Application.  Invoked by Component.
	<br>
	test:
	<br>
	  &nbsp; <code><b>wget -q -O - 'http://localhost:8080/bard/db/badapple/prom?smiles=...'</b></code>
*/
public class db_badapple_restletApp extends Application
{  
  public db_badapple_restletApp()
  {
    setName("Restlet BADAPPPLE db promiscuity Application");
    setDescription("Restlet BADAPPPLE db promiscuity Application");
    setOwner("UNM Translational Informatics, Albuquerque, New Mexico");
    setAuthor("Jeremy J Yang");
  }

  @Override
  public Restlet createInboundRoot()
  {
    Router router = new Router(getContext());
    // Note that Component Router routes "/bard/db/badapple" to this Application (Restlet Root URI).
    router.attach("/prom", db_badapple_prom_restletServer.class, Router.MODE_FIRST_MATCH);
    router.attachDefault(notfound_restletServer.class);
    return router;
  }
}
