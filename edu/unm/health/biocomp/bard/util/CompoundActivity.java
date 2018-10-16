package edu.unm.health.biocomp.bard.util;

import java.io.*;
import java.util.*;

/**	Simple class for compound activity statistics.
*/
public class CompoundActivity
{
  public Integer aTested;
  public Integer aActive;
  public Integer sTested;
  public Integer sActive;
  CompoundActivity(int _aTested,int _aActive,int _sTested,int _sActive)
  {
    this.aTested=_aTested;
    this.aActive=_aActive;
    this.sTested=_sTested;
    this.sActive=_sActive;
  }
}
