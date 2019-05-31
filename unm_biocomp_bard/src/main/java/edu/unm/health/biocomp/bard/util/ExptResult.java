package edu.unm.health.biocomp.bard.util;

class ExptResult
{
  public Long exptDataId;
  public Long eid;
  public Long sid;
  public Long cid;
  public Integer classification;
  public Double potency;
  public Integer score;
  public Integer outcome;
  ExptResult(long _exptDataId,long _eid,long _sid,long _cid,int _classification,double _potency,int _score,int _outcome)
  {
    this.exptDataId=_exptDataId;
    this.eid=_eid;
    this.sid=_sid;
    this.cid=_cid;
    this.classification=_classification;
    this.potency=_potency;
    this.score=_score;
    this.outcome=_outcome;
  }
  boolean isActive()
  {
    return (outcome!=null && outcome==2);
  }
}
