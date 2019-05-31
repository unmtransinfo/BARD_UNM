package edu.unm.health.biocomp.bard.restlet;

import java.io.*;
import java.util.*;

import org.restlet.*;
import org.restlet.resource.*;
import org.restlet.data.*;
import org.restlet.representation.Representation;
import org.restlet.routing.*;
import org.restlet.util.*;

/**	Property calculator Application.  Invoked by Component.
	Accept smiles via url, or if absent, use test smiles.
	
	test:
	wget -q -O - 'http://localhost:8182/bard/calc/logp?smiles=NC(C)Cc1ccccc1'
*/
public class calculator_restletApp extends Application
{  

  /**	Only for testing.
  */
  public static void main(String[] args) throws Exception
  {
    Server calcServer = new Server(Protocol.HTTP, 8111);
    calcServer.setNext(new calculator_restletApp());
    calcServer.start();
  }

  public calculator_restletApp()
  {
    setName("RESTful molecular property calculator");
    setDescription("Example Restlet application, using ChemAxon JChem");
    setOwner("UNM Translational Informatics, Albuquerque, New Mexico");
    setAuthor("Jeremy J Yang");
  }

  @Override
  public Restlet createInboundRoot()
  {
    Router router = new Router(getContext());
    router.attach("/bard/calc/logp", logp_restletServer.class, Router.MODE_FIRST_MATCH);
    router.attach("/bard/calc/logd", logd_restletServer.class, Router.MODE_FIRST_MATCH);
    router.attach("/logp", logp_restletServer.class, Router.MODE_FIRST_MATCH); //for Tomcat
    router.attach("/logd", logd_restletServer.class, Router.MODE_FIRST_MATCH); //for Tomcat
    //router.attach("http://localhost:8111/bard/calc/logp", logp_restletServer.class);
    //router.attach("http://localhost:8111/bard/calc/logd", logd_restletServer.class);
    router.attachDefault(notfound_restletServer.class);
    return router;

//    return new Restlet() {
//      @Override
//      public void handle(Request request, Response response)
//      {
//        String entity = "Method : "+request.getMethod()+"\n"
//          + "Resource URI : "+request.getResourceRef()+"\n"
//          + "IP address : "+request.getClientInfo().getAddress()+"\n"
//          + "Agent name : "+request.getClientInfo().getAgentName()+"\n"
//          + "Agent version: "+request.getClientInfo().getAgentVersion()+"\n" ;
//        response.setEntity(entity, MediaType.TEXT_PLAIN);
//      }
//    };
  }

}
