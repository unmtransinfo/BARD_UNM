package edu.unm.health.biocomp.bard.util;

import java.io.*;
import java.util.*;

/**	Simple class for scaffold activity statistics.
*/
public class ScaffoldActivity
{
  public Integer cTotal;
  public Integer cTested;
  public Integer cActive;
  public Integer aTested;
  public Integer aActive;
  public Integer sTested;
  public Integer sActive;
  ScaffoldActivity(int _cTotal,int _cTested,int _cActive,int _aTested,int _aActive,int _sTested,int _sActive)
  {
    this.cTotal=_cTotal;
    this.cTested=_cTested;
    this.cActive=_cActive;
    this.aTested=_aTested;
    this.aActive=_aActive;
    this.sTested=_sTested;
    this.sActive=_sActive;
  }
}
