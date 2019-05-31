package edu.unm.health.biocomp.bard.test;

import java.io.*;
import java.util.*;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

/**	This resource class invoked by the hello_jerseyApp Application via web.xml.
	In web.xml we have:
  <pre>
  &lt;servlet&gt;
    &lt;servlet-name&gt;hellojersey&lt;/servlet-name&gt;
    &lt;servlet-class&gt;
      com.sun.jersey.spi.container.servlet.ServletContainer
    &lt;/servlet-class&gt;
    &lt;init-param&gt;
      &lt;param-name&gt;javax.ws.rs.Application&lt;/param-name&gt;
      &lt;param-value&gt;edu.unm.health.biocomp.bard.hello_jerseyApplication&lt;/param-value&gt;
    &lt;/init-param&gt;
    &lt;load-on-startup /&gt;
  &lt;/servlet&gt;
  &lt;servlet-mapping&gt;
    &lt;servlet-name&gt;hellojersey&lt;/servlet-name&gt;
    &lt;url-pattern&gt;/hellojersey/*&lt;/url-pattern&gt;
  &lt;/servlet-mapping&gt;
  </pre>
	Then test with:
	<br>
	<code><b>wget -q -O - 'http://localhost:8080/bard/hellojersey/hello'</b></code>
*/
@Path("hello")
public class hello_jerseyResource
{
  @GET 
  @Produces("text/plain")
  public String getClichedMessage()
  {
    return "Hello Jersey!  Which exit?\n";
  }
}
