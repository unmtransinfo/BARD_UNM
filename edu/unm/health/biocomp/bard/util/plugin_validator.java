package edu.unm.health.biocomp.bard.util;

import java.io.*;
import java.util.*;

import gov.nih.ncgc.bard.rest.BARDConstants;
import gov.nih.ncgc.bard.tools.*; // Util, PluginValidator
import gov.nih.ncgc.bard.plugin.IPlugin;


/**	BARD plugin validator.
	@author Jeremy Yang
*/
public class plugin_validator
{
  public static void main(String[] args)
	throws Exception
  {
    if (args.length < 1)
    {
      System.out.println("ERROR: WAR file path arg required.");
      System.out.println("syntax: plugin_validator [-v] <WARPATH>");
      System.exit(1);
    }
    boolean verbose=false;
    if (args.length > 1)
    {
      if (args[0].equals("-v")) verbose=true;
    }
    String warpath=args[args.length-1];
    boolean ok=false;
    PluginValidator pv = new PluginValidator();
    try {
      System.out.println("WARPATH: "+warpath);
      ok=pv.validate(warpath);
    } catch (InstantiationException e) {
      System.out.println("ERROR: (InstantiationException) "+e.getMessage());
    }
    System.out.println("validation status: "+(ok?"PASS":"FAIL"));
    if (ok && !verbose)
    {
      int i=0;
      for (String s: pv.getErrors()) ++i;
      System.out.println("validator messages supressed: "+i);
      System.out.println("(Use -v arg to see messages on PASS.)");
    }
    else
    {
      for (String s: pv.getErrors()) System.out.println(s);
    }
  }
}
