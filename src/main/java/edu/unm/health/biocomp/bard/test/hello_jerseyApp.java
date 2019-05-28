package edu.unm.health.biocomp.bard.test;

import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.Application;
import javax.ws.rs.ApplicationPath;

/**	Application for testing hello_jerseyResource.class.
 */
@ApplicationPath("/")
public class hello_jerseyApp extends Application
{
  @Override
  public Set<Class<?> > getClasses()
  {
    final Set<Class<?> > classes = new HashSet<Class<?> >();
    // register root resource
    classes.add(hello_jerseyResource.class);
    return classes;
  }
}
