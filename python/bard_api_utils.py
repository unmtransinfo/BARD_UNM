#!/usr/bin/env python
##############################################################################
### bard_api_utils.py - utility functions for access to BARD REST API.
###
### We should be able to use the schema, e.g.  /experiments/_schema,
### to automate the datatype handling.
###
### Jeremy Yang
### 22 Jul 2014
##############################################################################
import sys,os,re,getopt,time,types
import urllib,urllib2,httplib,json
#
import time_utils
import rest_utils_py2 as rest_utils
#
PROG=os.path.basename(sys.argv[0])
#
API_HOST='bard.nih.gov'
API_VERSION='latest'
BASE_PATH='/api'
#
RESOURCES=['projects','assays','experiments','targets','compounds','substances','documents','biology','exptdata']
#
##############################################################################
def Describe(base_uri,verbose=0):
  txt=''
  for res in RESOURCES:
    try:
      rval=rest_utils.GetURL(base_uri+'/%s/_info'%res,{},parse_json=True,verbose=verbose)
      txt+=('%s\n'%(str(rval)))
    except urllib2.HTTPError, e:
      print >>sys.stderr, 'HTTP Error (%s): %s'%(res,e)
      continue
  return txt

#############################################################################
def Counts(base_uri,verbose=0):
  txt=''
  for res in RESOURCES:
    try:
      n=rest_utils.GetURL(base_uri+'/%s/_count'%res,{},parse_json=True,verbose=verbose)
      txt+=('%s count: %d\n'%(res,n))
    except urllib2.HTTPError, e:
      print >>sys.stderr, 'HTTP Error (%s): %s'%(res,e)
      continue
    except Exception, e:
      print >>sys.stderr, 'Exception (%s): %s'%(res,e)
      continue
  return txt

#############################################################################
### Now with filter=[tested] this can be done more efficiently.
#############################################################################
def ListSubstancesTested(base_uri,fout,nskip=0,nmax=0,nchunk=500,verbose=0):
  n_all=0; n_out=0; n_err=0;
  t0=time.time()
  link=('/substances?filter=[tested]&skip=%d&top=%d&expand=true'%(nskip,nchunk))
  done=False
  while True:
    rval=rest_utils.GetURL(base_uri+link,{},parse_json=True,verbose=verbose)
    if not type(rval) is dict:
      print >>sys.stderr, ('DEBUG: uri="%s" ; rval="%s"'%(base_uri+link,str(rval)))
      break
    substances=rval['collection']
    for substance in substances:
      if not substance: continue
      if verbose>0:
        if n_all%100==0:
          print >>sys.stderr, 'n_all: %d  n_out: %d  n_err: %d'%(n_all,n_out,n_err),
          print >>sys.stderr, 'elapsed time: %s'%time.strftime('%Hh:%Mm:%Ss',time.gmtime(time.time()-t0))
      n_all+=1
      sid=substance['sid']
      smi=substance['smiles']
      cid=substance['cid']
      #source=substance['source']
      if not smi:
        print >>sys.stderr, ('ERROR: (sid=%d) SMILES missing.'%sid)
        n_err+=1
        continue
      fout.write("%s %d %d\n"%(smi,sid,cid))
      n_out+=1
      if nmax>0 and n_out>=nmax:
        done=True
        break
    if done: break
    link=rval['link']
    if not link: break  ## END of SIDs
  return n_all,n_out,n_err

#############################################################################
def ListProbes(base_uri,fout,verbose=0):
  n_all=0; n_out=0; n_err=0;
  n_htc=0;
  pids=set(); cids=set(); aids=set();
  ctags=[	#compound-tag
	'smiles',
	'name',
	'tpsa',
	'exactMass',
	'probeId',
	'mwt',
	'complexity',
	'rotatable',
	'highlight',
	'compoundClass',
	'iupacName',
	'probeAnnotations',
	'numAssay',
	'hbondAcceptor',
	'hbondDonor',
	'numActiveAssay',
	'xlogp'
	]
  cstags=[	#compound-summary-tag
	#'hitTarget',
	'hitAssays',
	#'testedAssays',
	#'hitExptdata',
	#'testedExptdata',
	'ntest',
	'nhit'
	]
  tags=['PID','CID']+ctags+cstags
  fout.write(','.join(tags)+'\n')
  link=('/projects')
  n_project_total=0;
  rval=rest_utils.GetURL(base_uri+link,{},parse_json=True,verbose=verbose)
  projects=rval['collection']
  for link2 in projects:	#e.g. "/projects/17"
    pid=re.sub(r'^.*/','',link2)
    pids.add(int(pid))
    n_project_total+=1
    rval2=rest_utils.GetURL(base_uri+link2+'/probes',{},parse_json=True,verbose=verbose)
    probes=rval2
    for link3 in probes:	#e.g. "/compounds/5716367"
      cid=re.sub(r'^.*/','',link3)
      cids.add(int(cid))
      rval2=rest_utils.GetURL(base_uri+link3,{},parse_json=True,verbose=verbose)
      cpds=rval2
      if not cpds:
        print >>sys.stderr, ('ERROR: no cpds: %s'%link2)
        n_err+=1
        continue
      cpd=cpds[0]
      rval3=rest_utils.GetURL(base_uri+link3+'/summary',{},parse_json=True,verbose=verbose)
      cpdsum=rval3
      if not cpdsum:
        print >>sys.stderr, ('ERROR: no summary: %s'%link2+'/summary')
        n_err+=1
        continue
      vals=[pid,cid]
      for tag in ctags:
        if tag=='probeAnnotations' and cpd.has_key(tag) and cpd[tag]:
          urls=map(lambda x:x['url'],cpd[tag])
          urls=list(set(urls))  ## uniquify
          if None in urls: urls.remove(None)  ## remove JSON nulls.
          try:
            vals.append(rest_utils.ToStringForCSV(','.join(urls),maxlen=0))
          except Exception, e:
            print >>sys.stderr, 'DEBUG: problem with urls: CID=%s'%(cid)
            print >>sys.stderr, 'Error (Exception): %s'%e
            vals.append('ERROR')
        elif cpd.has_key(tag):
          vals.append(rest_utils.ToStringForCSV(cpd[tag]))
        else:
          vals.append(rest_utils.ToStringForCSV(None))
      for tag in cstags:
        if tag=='hitTargetClasses' and cpdsum.has_key(tag) and cpdsum[tag]:
          vals.append(rest_utils.ToStringForCSV(','.join(cpdsum[tag].keys())))
          n_htc+=1
        elif tag=='hitAssays' and cpdsum.has_key(tag) and cpdsum[tag]:
          aid_strs=map(lambda x:x.replace('/assays/',''),cpdsum[tag])
          for aid in aid_strs:
            aids.add(int(aid))
          #print >>sys.stderr, 'DEBUG: "%s"'%(','.join(aid_strs))
          vals.append('"'+(','.join(aid_strs))+'"')
        elif cpdsum.has_key(tag):
          vals.append(rest_utils.ToStringForCSV(cpdsum[tag]))
        else:
          vals.append(rest_utils.ToStringForCSV(None))
      fout.write((','.join(vals))+'\n')
      n_out+=1

  print >>sys.stderr, 'n_project_total: %d (unique: %d)'%(n_project_total,len(pids))
  print >>sys.stderr, 'n_probe_total: %d (unique: %d)'%(n_out,len(cids))
  print >>sys.stderr, 'unique assays: %d'%(len(aids))
  #print >>sys.stderr, 'n_probe_htc (hitTargetClasses): %d'%n_htc
  print >>sys.stderr, 'n_err: %d'%n_err

  return n_all,n_out,n_err

#############################################################################
### Now with filter=[tested] this can be done more efficiently.
#############################################################################
def ListCompoundsTested(base_uri,fout,nskip=0,nmax=0,nchunk=500,verbose=0):
  n_all=0; n_out=0; n_err=0;
  t0=time.time()
  link=('/compounds?filter=[tested]&expand=true&skip=%d&top=%d'%(nskip,nchunk))
  done=False
  while True:
    rval=rest_utils.GetURL(base_uri+link,{},parse_json=True,verbose=verbose)
    if not rval:
      print >>sys.stderr, ('ERROR: no response: %s'%link)
      break
    compounds=rval['collection']
    for compound in compounds:
      if not compound: continue
      if verbose>0:
        if n_all%100==0:
          print >>sys.stderr, 'n_all: %d  n_out: %d  n_err: %d'%(n_all,n_out,n_err),
          print >>sys.stderr, 'elapsed time: %s'%time.strftime('%Hh:%Mm:%Ss',time.gmtime(time.time()-t0))
      n_all+=1
      cid=compound['cid']
      smi=compound['smiles']
      if not smi:
        print >>sys.stderr, ('ERROR: (cid=%d) SMILES missing.'%cid)
        n_err+=1
        continue
      fout.write("%s %d\n"%(smi,cid))
      n_out+=1
      if nmax>0 and n_out>=nmax:
        done=True
        break
    if done: break
    link=rval['link']
    if not link: break  ## END of CIDs
  return n_all,n_out,n_err

#############################################################################
def ListSubstancesFromSource(base_uri,fout,source,nskip=0,nmax=0,nchunk=500,verbose=0):
  ## For given source, write all substances to smiles file.
  n_all=0; n_out=0; n_err=0;
  t0=time.time()
  link=('/substances?filter=%s[source_name]&expand=true&skip=%d&top=%d'%(source,nskip,nchunk))
  while True:
    rval=rest_utils.GetURL(base_uri+link,{},parse_json=True,verbose=verbose)
    if not rval: continue ## ERROR
    try:
      substances=rval['collection']
    except Exception, e:
      print >>sys.stderr, 'Error (Exception): %s'%e
      print >>sys.stderr, 'DEBUG: rval="%s"'%rval
      break
    if not substances:
      break  ## may be link past end of SIDs (feature)
    for substance in substances:
      if verbose>0:
        if n_all%1000==0:
          print >>sys.stderr, 'n_all: %d  n_out: %d  n_err: %d'%(n_all,n_out,n_err),
          print >>sys.stderr, 'elapsed time: %s'%time.strftime('%Hh:%Mm:%Ss',time.gmtime(time.time()-t0))
      n_all+=1
      sid=substance['sid']
      cid=substance['cid']
      smi=substance['smiles']
      fout.write('%s %d %d\n'%(smi,sid,cid))
      fout.flush()
      n_out+=1
      if nmax>0 and n_out>=nmax:
        break
    link=rval['link']
    if not link: break ## END of SIDs
  return n_all,n_out,n_err

#############################################################################
def ListCompoundsFromSource(base_uri,fout,source,nskip=0,nmax=0,nchunk=500,verbose=0):
  ## For given source, write all compounds to smiles file.
  n_all=0; n_out=0; n_err=0;
  cids={}
  t0=time.time()
  link=('/substances?filter=%s[source_name]&expand=true&skip=%d&top=%d'%(source,nskip,nchunk))
  while True:
    rval=rest_utils.GetURL(base_uri+link,{},parse_json=True,verbose=verbose)
    if not rval: continue ## ERROR
    substances=rval['collection']
    if not substances:
      break  ## may be link past end of SIDs (feature)
    for substance in substances:
      if verbose>0:
        if n_all%1000==0:
          print >>sys.stderr, 'n_all: %d  n_out: %d  n_err: %d'%(n_all,n_out,n_err),
          print >>sys.stderr, 'elapsed time: %s'%time.strftime('%Hh:%Mm:%Ss',time.gmtime(time.time()-t0))
      n_all+=1
      cid=substance['cid']
      if cids.has_key(cid): continue
      cids[cid]=True
      #sid=substance['sid']
      smi=substance['smiles']
      fout.write('%s %d\n'%(smi,cid))
      fout.flush()
      n_out+=1
      if nmax>0 and n_out>=nmax:
        break
    link=rval['link']
    if not link: break ## END of SIDs
  return n_all,n_out,n_err

#############################################################################
### Updating tags to adjust to new schema is burdensome.  Alternatives?
#############################################################################
def ListProjects(base_uri,fout,verbose=0):
  n_all=0; n_out=0; n_err=0;
  ptags=[
	'bardProjectId',
	'capProjectId',
	'name',
	'description',
	'source',
	'category',
	'type',
	'classification',
	'experimentCount'
	]

  ## project-annotations discontinued!?
  patags={'assay provider name':None,'laboratory name':None,'grant number':None}	##project-annotation tags (context name:"project management")

  ttags=['biology','name','dictLabel','dictId','extId','resourcePath']
  tags=(ptags+map(lambda s:'target:'+s,ttags))
  tags+=(map(lambda s:'projann:'+s,patags.keys()))
  tags.extend(['assayCount','targetCount','probeCount'])
  fout.write(','.join(tags)+'\n')
  link=('/projects?expand=true')
  bids={};
  n_project_total=0;
  while True:
    rval=rest_utils.GetURL(base_uri+link,{},parse_json=True,verbose=verbose)
    if not rval:
      n_err+=1
      continue ## ERROR
    projects=rval['collection']
    for project in projects:
      n_all+=1
      n_target=0; n_assay=0; n_probe=0;
      for tag in patags.keys():
        patags[tag]=None

      pid=project['bardProjectId']
      targets=project['targets']
      if targets and len(targets)>0:
        n_target=len(targets)
        target=PreferredBiology(targets)	##1 only for now...
        for bid in Biologys2BIDs(targets):
          bids[bid]=True
      else:
        target=None
      aids=project['aids']
      if aids and len(aids)>0:
        n_assay=len(aids)
      probes=project['probes']
      if probes and len(probes)>0:
        n_probe=len(probes)

      ### This stopped working...
#     link2='/projects/%d/annotations'%pid
#     rval2=rest_utils.GetURL(base_uri+link2,{},parse_json=True,verbose=verbose)
#     if not rval2:
#       n_err+=1
#       continue ## ERROR
#     contexts=rval2['contexts']
#     for context in contexts:
#       if context.has_key('name') and context['name']=='project management':
#         if context.has_key('comps') and context['comps']:
#           annos=context['comps']
#           for anno in annos:
#             for tag in patags.keys():
#               if anno.has_key('key') and anno['key']==tag:
#                 val=anno['value']
#                 if not val: val=anno['display']
#                 if val:
#                   patags[tag]=val

      vals=[]
      for tag in ptags:
        vals.append(rest_utils.ToStringForCSV(project[tag]))
      for ttag in ttags:
        if target:
          vals.append(rest_utils.ToStringForCSV(target[ttag]))
        else:
          vals.append('')

      for tag in patags.keys():
        vals.append(rest_utils.ToStringForCSV(patags[tag]))

      vals.append('%d'%n_assay)
      vals.append('%d'%n_target)
      vals.append('%d'%n_probe)

      fout.write((','.join(vals))+'\n')
      n_out+=1
    link=rval['link']
    if not link: break  ## END of PIDs

  print >>sys.stderr, 'n_project_total: %d'%n_all
  print >>sys.stderr, 'n_target_total_uniq: %d'%len(bids.keys())

  return n_all,n_out,n_err

#############################################################################
def ListProjects_UNM(base_uri,fout,verbose=0):
  ### How to identify UNM projects?  "source" is currently empty...
  print >>sys.stderr, '[NOT YET IMPLEMENTED]'

#############################################################################
### Discontinued feature?  On 11/14, works on straw but not latest.
### No more project annotations apparently.
#############################################################################
def Project2Labname(base_uri,pid):
  link='/projects/%d/annotations'%pid
  #print >>sys.stderr, 'DEBUG: %s/projects/%d/annotations'%(base_uri,pid)
  rval=rest_utils.GetURL(base_uri+link,{},parse_json=True,verbose=0)
  if not rval:
    return None
  contexts=rval['contexts']
  for context in contexts:
    if context.has_key('name') and context['name']=='project management':
      if context.has_key('comps') and context['comps']:
        annos=context['comps']
        for anno in annos:
          if anno.has_key('key') and anno['key']=='laboratory name':
            val=anno['value']
            if val: return val
            else: return anno['display']
  return None

#############################################################################
### Discontinued feature?  On 11/14, works on straw but not latest.
#############################################################################
def Pid2CapPid(base_uri,pid):
  project=rest_utils.GetURL(base_uri+'/projects/%d'%pid,{},parse_json=True,verbose=0)
  #print >>sys.stderr, 'DEBUG: %s/projects/%d'%(base_uri,pid)
  if not project:
    return None
  if project.has_key('capProjectId'):
    return project['capProjectId']
  else:
    return None

#############################################################################
### Each experiment associated with one assay (bardAssayId).  Via the
### assay associate with targets (biology's).
### 
### How to associate an experiment with institution?  Arguably this should
### be an experiment annotation.  Seems we need to get via project[s].
### 
### Note: for Badapple HTS experiments defined with mincpds=20000.
#############################################################################
def ListExperiments(base_uri,fout,mincpds=0,verbose=0):
  n_all=0; n_out=0; n_err=0;
  etags=[
	'bardExptId',
	'capExptId',
	'name',
	'description',
	'status',
	'hasProbe',
	'confidenceLevel',
	'pubchemAid',
	'bardAssayId',
	'capAssayId',
	'substances',	#number of substances
	'compounds',	#number of compounds
	'activeCompounds'
	]

  ### experiment-annotations discontinued???
  eatags={'project lead name':None}	##experiment-annotation tags (context name:"project lead name")

  ttags=[
	'biology',	#PROTEIN|PROCESS|GENE|etc.
	'name',
	'dictLabel',
	'dictId',
	'extId',
	'resourcePath'
	]
  tags=etags[:]
  tags.extend(['projectId'])	# only 1st, possibly more
  tags.extend(['capProjectId'])	# only 1st, possibly more
  tags.extend(eatags.keys())
  tags.extend(map(lambda s:'target:'+s,ttags))
  tags.extend(['targetCount'])
  tags.extend(['project:labName'])
  fout.write((','.join(tags))+'\n')
  link=('/experiments?expand=true&top=100')
  n_experiment_total=0;
  n_experiment_notarget=0;
  n_experiment_filtered=0;
  aids={};
  bids={};	#biology IDs
  pids={};	#project IDs
  n_target_total=0;
  while True:
    rval=rest_utils.GetURL(base_uri+link,{},parse_json=True,verbose=verbose)
    if not rval:
      n_err+=1
      continue ## ERROR
    experiments=rval['collection']
    for experiment in experiments:
      n_all+=1
      n_target=0;
      eid=experiment['bardExptId']
      aid=experiment['bardAssayId']
      aids[aid]=True
      for tag in eatags.keys():
        eatags[tag]=None
      if mincpds:
        cpd_count=experiment['compounds']
        if cpd_count<mincpds:
          #print >>sys.stderr, 'DEBUG: expt cpd_count < %d'%mincpds
          n_experiment_filtered+=1
          continue

      targets=AID2Targets(base_uri,aid,verbose) ##broken?
      if targets and len(targets)>0:
        n_target=len(targets)
        target=PreferredBiology(targets)	##only 1 for now...
        for bid in Biologys2BIDs(targets):
          bids[bid]=True
      else:
        target=None
      if not target:
        n_experiment_notarget+=1

      vals=[]
      pids_this=None
      for etag in etags:
        vals.append(rest_utils.ToStringForCSV(experiment[etag]))

      etag='projectIdList'
      if experiment.has_key('projectIdList'):
        pids_this=experiment[etag]
        if pids_this: pid=pids_this[0]
        else: pid=None
        cap_pids_this=map(lambda x: Pid2CapPid(base_uri,x),pids_this)
        if cap_pids_this: cap_pid=cap_pids_this[0]
        else: cap_pid=None
        vals.append(rest_utils.ToStringForCSV(pid))
        vals.append(rest_utils.ToStringForCSV(cap_pid))

      ### Broken in latest, ok in straw (May 2014)...
      link2=('/experiments/%d/annotations'%eid)
      rval2=rest_utils.GetURL(base_uri+link2,{},parse_json=True,verbose=verbose)
      if not rval2:
        n_err+=1
        continue ## ERROR
      contexts=rval2['contexts']
      for context in contexts:
        if context.has_key('name') and context['name']=='project lead name':
          if context.has_key('comps') and context['comps']:
            annos=context['comps']
            for anno in annos:
              for tag in eatags.keys():
                if anno.has_key('key') and anno['key']==tag:
                  val=anno['value']
                  if not val: val=anno['display']
                  if val:
                    eatags[tag]=val

      for tag in eatags.keys():
        vals.append(rest_utils.ToStringForCSV(eatags[tag]))

      for ttag in ttags:
        if target:
          vals.append(rest_utils.ToStringForCSV(target[ttag]))
        else:
          vals.append('')
      vals.append('%d'%n_target)
      if pids_this: vals.append(rest_utils.ToStringForCSV(Project2Labname(base_uri,pids_this[0])))

      fout.write((','.join(vals))+'\n')
      n_out+=1
      n_experiment_total+=1
      n_target_total+=n_target

    link=rval['link']
    if not link: break  ## END of EIDs

  print >>sys.stderr, 'n_experiment_total: %d'%n_all
  print >>sys.stderr, 'n_experiment_out: %d'%n_out
  print >>sys.stderr, 'n_assay_total_uniq: %d'%len(aids.keys())
  print >>sys.stderr, 'n_target_total: %d'%n_target_total
  print >>sys.stderr, 'n_target_total_uniq: %d'%len(bids.keys())
  print >>sys.stderr, 'n_experiment_notarget: %d'%n_experiment_notarget
  print >>sys.stderr, 'n_experiment_filtered: %d'%n_experiment_filtered

  return n_all,n_out,n_err

#############################################################################
def ListBiologyTypes(base_uri,verbose=0):
  link=('/biology/types')
  rval=rest_utils.GetURL(base_uri+link,{},parse_json=True,verbose=verbose)
  return rval

#############################################################################
def ListBiologyCounts(base_uri,fout,verbose=0):
  btypes=ListBiologyTypes(base_uri,verbose)
  for btype in btypes:
    link=('/biology/types/%s/_count'%btype)
    rval=rest_utils.GetURL(base_uri+link,{},parse_json=True,verbose=verbose)
    try:
      count=int(rval)
    except Exception, e:
      if verbose>0:
        print >>sys.stderr, 'Error (Exception): %s'%e
        print >>sys.stderr, 'DEBUG: rval=%s'%rval
      count=0
    print >>sys.stderr, '\tbiology/%-10s: %6d'%(btype,count)

#############################################################################
def ListBiologys(base_uri,fout,verbose=0):
  n_all=0; n_out=0; n_err=0;
  btypes=ListBiologyTypes(base_uri,verbose)
  for btype in btypes:
    n_all_this,n_out_this,n_err_this = ListBiologyOfType(base_uri,fout,btype,verbose)
    n_all+=n_all_this; n_out+=n_out_this; n_err+=n_err_this;
  return n_all,n_out,n_err

#############################################################################
def ListGenes(base_uri,fout,verbose=0):
  return ListBiologyOfType(base_uri,fout,'GENE',verbose)

#############################################################################
def ListProteins(base_uri,fout,verbose=0):
  return ListBiologyOfType(base_uri,fout,'PROTEIN',verbose)

#############################################################################
### [ ] TO DO:
### Link biologys of type PROTEIN to target classes via Uniprot, e.g.
### /targets/accession/P25116
### Then extract Panther classes, e.g.
#  "classes": [
#    {
#      "levelIdentifier": "1.01.00.00.00", 
#      "source": "panther", 
#      "description": "A molecular structure within a cell or on the cell surface characterized by selective binding of a specific substance and a specific physiologic effect that accompanies the binding.", 
#      "id": "PC00197", 
#      "name": "receptor"
#    }, 
#    {
#      "levelIdentifier": "1.01.01.00.00", 
#      "source": "panther", 
#      "description": "Cell surface receptors that are coupled to G proteins and have 7 transmembrane spanning domains.", 
#      "id": "PC00021", 
#      "name": "G-protein coupled receptor"
#    }
# Also report what % of proteins are so classified.
#
#############################################################################
def ListProteins_classified(base_uri,fout,verbose=0):
  n_all=0; n_out=0; n_err=0;
  n_classified=0;
  btags=[
	'biology',
	'name',
	'dictId',
	'dictLabel',
	'entity',
	'entityId',
	'extRef',
	'extId',
	'serial'
	]
  ctags=[	## protein classification tags
	'source',
	'id',
	'name',
	'levelIdentifier'
	]
  tags=(btags+map(lambda s:'targetclass:'+s,ctags))
  fout.write(','.join(tags)+'\n')
  link=('/biology/types/PROTEIN?expand=true')
  rval=rest_utils.GetURL(base_uri+link,{},parse_json=True,verbose=verbose)

  if not rval:
    n_err+=1
    if verbose:
      print >>sys.stderr, 'Error: no biologys, type: %s'%btype
  else:
    bios=rval
    for bio in bios:
      n_all+=1
      vals=[]
      for tag in btags:
        vals.append(rest_utils.ToStringForCSV(bio[tag]))

      if bio['dictId']==1398 or bio['dictLabel']=='Uniprot accession number':
        uid=bio['extId']


        ### NOTE: OK in v17.2.
        ### NOTE: Not OK in v17.3, since /targets deprecated.
        ### NOTE: Now we must instead use the "protclass" plugin.
#
#       link2=('/targets/accession/%s'%uid)
#       tgt=rest_utils.GetURL(base_uri+link2,{},parse_json=True,verbose=verbose)
#       clevel=sys.maxint
#       if tgt and tgt.has_key('classes'):
#         n_classified+=1
#         for protclass in tgt['classes']:
#           if protclass.has_key('source') and protclass['source']=='panther':
#             ##Select higher levelIdentifier.
#             if PantherLevel(protclass['levelIdentifier'])<clevel:
#               clevel=PantherLevel(protclass['levelIdentifier'])
#               while len(vals)>len(btags):
#                 vals.pop()
#               for tag in ctags:
#                 vals.append(rest_utils.ToStringForCSV(protclass[tag]))


        link2=('/plugins/protclass/panther/%s'%uid)
        rval=rest_utils.GetURL(base_uri+link2,{},parse_json=True,verbose=verbose)
        clevel=sys.maxint
        if rval.has_key(uid) and rval[uid]:
          n_classified+=1
          for protclass in rval[uid]:
            if protclass.has_key('source') and protclass['source']=='panther':
              ##Select higher levelIdentifier.
              if PantherLevel(protclass['levelIdentifier'])<clevel:
                clevel=PantherLevel(protclass['levelIdentifier'])
                while len(vals)>len(btags):
                  vals.pop()
                for tag in ctags:
                  vals.append(rest_utils.ToStringForCSV(protclass[tag]))


      fout.write((','.join(vals))+'\n')
      n_out+=1

  print >>sys.stderr, 'n_classified: %d'%n_classified
  return n_all,n_out,n_err

#############################################################################
### Five hierarchical classification levels, 1=highest to 5=lowest
#############################################################################
def PantherLevel(panther_level_id):
  vals=re.split(r'\.',panther_level_id)
  level=0
  for val in vals:
    if int(val)==0: break
    level+=1
  return level
    
#############################################################################
def ListProcesses(base_uri,fout,verbose=0):
  return ListBiologyOfType(base_uri,fout,'PROCESS',verbose)

#############################################################################
def ListBiologyOfType(base_uri,fout,btype,verbose=0):
  n_all=0; n_out=0; n_err=0;
  tags=[
	'biology',
	'name',
	'dictId',
	'dictLabel',
	'entity',
	'entityId',
	'extRef',
	'extId',
	'serial'
	]
  fout.write(','.join(tags)+'\n')
  link=('/biology/types/%s?expand=true'%btype)

  rval=rest_utils.GetURL(base_uri+link,{},parse_json=True,verbose=verbose)
  if not rval:
    n_err+=1
    if verbose:
      print >>sys.stderr, 'Error: no biologys, type: %s'%btype
  else:
    bios=rval
    for bio in bios:
      n_all+=1
      vals=[]
      for tag in tags:
        vals.append(rest_utils.ToStringForCSV(bio[tag]))
      fout.write((','.join(vals))+'\n')
      n_out+=1

  return n_all,n_out,n_err

#############################################################################
def PreferredBiology(biologys):
  if not biologys or len(biologys)==0:
    return None
  prefBioTypes=['PROTEIN','GENE','GO','PROCESS'] ##preferred types in order
  for btype in prefBioTypes:
    for biology in biologys:
      if biology['biology']==btype:
        return biology
  biology=biologys[0]	##default is 1st
  return biology

#############################################################################
def Biologys2BIDs(biologys):
  bids={}
  if not biologys or len(biologys)==0:
    return []
  for biology in biologys:
    if biology.has_key('serial'):
      bids[biology['serial']]=True
  bids=bids.keys()
  bids.sort()
  return bids

#############################################################################
### Assays = assay definitions.  Each assay has a list of targets
### (biology entities).
### Also generate categorical statistics:
###	source
###	designedBy
###	assay type
###	assay format
###	detection method type
#############################################################################
def ListAssays(base_uri,fout,mincpds=0,verbose=0):
  n_all=0; n_out=0; n_err=0;
  cats=[
	'source',
	'designedBy'
  ]
  atags=['bardAssayId','capAssayId','name','source','designedBy','title',
	'assayType','assayStatus','deposited','updated','grantNo'
	##,'description','comments'	## (verbose)
	]
  ttags=['biology','name','dictLabel','dictId','extId','resourcePath']
  minann_tags=['assay type','assay format','detection method type']
  counts={};
  for cat in cats+minann_tags: counts[cat]={}
  tags=(atags+minann_tags+map(lambda s:'target:'+s,ttags))
  tags.extend(['targetCount'])
  fout.write(','.join(tags)+'\n')

  ### This now broken... (No more paginated list of all assay entities?)
  link=('/assays?expand=true&top=100')
  n_assay_total=0;
  n_target_total=0;
  bids={}; #biology IDs
  while True:
    rval=rest_utils.GetURL(base_uri+link,{},parse_json=True,verbose=verbose)
    if not rval:
      n_err+=1
      continue ## ERROR
    assays=rval['collection']
    for assay in assays:
      n_all+=1
      #aid=assay['bardAssayId']
      #experiments=assay['experiments']
      minanns=assay['minimumAnnotations']
      targets=assay['targets']
      n_target=len(targets)
      if len(targets)>0:
        target=PreferredBiology(targets)	##1 only for now...
        for bid in Biologys2BIDs(targets):
          bids[bid]=True
      else:
        target=None
      vals=[]
      for atag in atags:
        vals.append(rest_utils.ToStringForCSV(assay[atag]))
      for minann_tag in minann_tags:
        try:
          vals.append(rest_utils.ToStringForCSV(minanns[minann_tag]))
        except Exception, e:
          #print >>sys.stderr, 'DEBUG: Error (Exception): %s'%e
          #print >>sys.stderr, 'DEBUG: minanns=%s'%str(minanns)
          vals.append('')
      for ttag in ttags:
        if target:
          vals.append(rest_utils.ToStringForCSV(target[ttag]))
        else:
          vals.append('')
      vals.append('%d'%n_target)
      fout.write((','.join(vals))+'\n')
      n_out+=1
      for cat in cats:
        if assay.has_key(cat):
          if not counts[cat].has_key(assay[cat]):
            counts[cat][assay[cat]]=0
          counts[cat][assay[cat]]+=1
      for cat in minann_tags:
        if minanns.has_key(cat):
          if not counts[cat].has_key(minanns[cat]):
            counts[cat][minanns[cat]]=0
          counts[cat][minanns[cat]]+=1
      n_assay_total+=1
      n_target_total+=n_target
    link=rval['link']
    if not link: break  ## END of AIDs

  for cat in cats+minann_tags:
    vals=counts[cat].keys()
    vals.sort()
    for val in vals:
      print >>sys.stderr, '%s: %28s : %3d assay definitions'%(cat,str(val)[:28],counts[cat][val])

  print >>sys.stderr, 'n_assay_total: %d'%n_assay_total
  print >>sys.stderr, 'n_target_total: %d'%n_target_total
  print >>sys.stderr, 'n_target_total_uniq: %d'%len(bids.keys())

  return n_all,n_out,n_err

#############################################################################
### Did expand=true stop working?  Yes.
### However, this should work: /assays/391/targets
### But, getting empty set for all ADIDs, and server error for /assays/391.
### Ah.  straw work, not latest.
#############################################################################
def AID2Targets(base_uri,aid,verbose=0):
  link=('/assays/%d'%aid)
  link=('/assays/%d?expand=true'%aid)
  #link=('/assays/%d/targets'%aid)
  assay=rest_utils.GetURL(base_uri+link,{},parse_json=True,verbose=verbose)
  if not assay:
    return None
  elif not assay.has_key('targets'):
    return []
  return assay['targets']

#############################################################################
### /targets resource obsoleted?  by biology ??
### Or is /targets ok but specifically for Uniprot protein targets?
### Classes includes Panther classification.
### [ ] to do: parse Panther classes
### 
### Note [15 Aug 2013]: 26095 classified / 512826 total
#############################################################################
def ListTargets(base_uri,fout,verbose=0):
  n_all=0; n_out=0; n_err=0;
  n_classified=0;
  tags=[
	'acc',
	'name',
	'status',
	'url',
	'taxId',
	'geneId',
	'description',
	'classes'
	]
  fout.write(','.join(tags)+'\n')
  link=('/targets?expand=true')
  while True:
    rval=rest_utils.GetURL(base_uri+link,{},parse_json=True,verbose=verbose)
    if not rval:
      n_err+=1
      continue ## ERROR
    targets=rval['collection']
    for target in targets:
      n_all+=1
      vals=[]
      for tag in tags:
        if tag=='classes' and len(target['classes'])>0:
          n_classified+=1
          vals.append('"['+((','.join(map(lambda x:x['source']+':'+x['id']+':'+x['name'],target['classes']))))+']"')
        else:
          vals.append(rest_utils.ToStringForCSV(target[tag]))
      fout.write((','.join(vals))+'\n')
      n_out+=1
    link=rval['link']
    if not link: break  ## END of PIDs
  print >>sys.stderr, ('n_classified: %d'%n_classified)
  return n_all,n_out,n_err

#############################################################################
def Cid2Smiles(base_uri,id,verbose=0):
  smi=rest_utils.GetURL(base_uri+'/compounds/%d/smiles'%id,{},parse_json=True,verbose=verbose)
  smi=re.sub(r'\s.*$','',smi)
  return smi

#############################################################################
def ExpActivity(base_uri,fout,eid,sids_query,verbose=0):
  tags=['bardExptId','sid','outcome']  ## mucho data; minimize storage
  fout.write('%s\n'%(','.join(tags)))
  n_expd=0; n_sub_act=0; n_cpd_act=0; 
  n_sam_act=0; n_sam_tst=0;
  cids={}; sids={};
  nskip=0; nchunk=500;
  link=None
  while True: ## process sids in chunks:
    if sids_query:
      print >>sys.stderr, 'DEBUG: len(sids_query)=%d'%len(sids_query)
      if len(sids_query)<nskip: break
      sidstr=(','.join(map(lambda x:str(x),sids_query[nskip:nskip+nchunk])))
      d={'sids':sidstr,'eids':str(eid)}
      expds=rest_utils.PostURL(base_uri+'/exptdata',{},d,parse_json=True,verbose=verbose)
    else:
      if not link:
        link='/experiments/%d/exptdata?expand=true&top=%d'%(eid,nchunk)
      print >>sys.stderr, 'DEBUG: link: %s'%link
      rval=rest_utils.GetURL(base_uri+link,{},parse_json=True,verbose=verbose)
      expds=rval['collection']

    for expd in expds:
      n_expd+=1
      eid=int(expd['bardExptId'])
      sid=int(expd['sid'])
      cid=int(expd['cid'])
      if sids_query:
        outcome=Expd2PCOutcome(expd)
      else:
        outcome=int(expd['outcome'])
      fout.write('%d,%d,%d\n'%(eid,sid,outcome))
      fout.flush()
      n_sam_tst+=1
      if not sids.has_key(sid):
        sids[sid]=False
      if not cids.has_key(cid):
        cids[cid]=False
      if outcome==2:
        n_sam_act+=1
        sids[sid]=True
        cids[cid]=True
    if sids_query:
      nskip+=nchunk
    else:
      link=rval['link']
      if not link: break  ## END of expdata IDs
  for cid,act in cids.items():
    if act:
      n_cpd_act+=1
  for sid,act in sids.items():
    if act:
      n_sub_act+=1

  print >>sys.stderr, ('n_expd: %d'%n_expd)
  print >>sys.stderr, ('n_cpd_tst: %d'%len(cids))
  print >>sys.stderr, ('n_cpd_act: %d'%n_cpd_act)
  print >>sys.stderr, ('n_sub_tst: %d'%len(sids))
  print >>sys.stderr, ('n_sub_act: %d'%n_sub_act)
  print >>sys.stderr, ('n_sam_tst: %d'%n_sam_tst)
  print >>sys.stderr, ('n_sam_act: %d'%n_sam_act)

  return

#############################################################################
def SubActivity(base_uri,fout,sid,eids_query,verbose=0):
  tags=['bardExptId','sid','outcome']  ## mucho data; minimize storage
  fout.write('%s\n'%(','.join(tags)))
  n_expd=0; n_cpd_act=0; n_exp_act=0; 
  n_sam_act=0; n_sam_tst=0;
  cids={}; eids={};
  nskip=0; nchunk=500;
  link=None
  while True: ## process eids in chunks:
    if eids_query:
      print >>sys.stderr, 'DEBUG: len(eids_query)=%d'%len(eids_query)
      if len(eids_query)<nskip: break
      eidstr=(','.join(map(lambda x:str(x),eids_query[nskip:nskip+nchunk])))
      d={'sids':str(sid),'eids':eidstr}
      expds=rest_utils.PostURL(base_uri+'/exptdata',{},d,parse_json=True,verbose=verbose)
    else:
      if not link:
        link='/substances/%d/exptdata?expand=true&top=%d'%(sid,nchunk)
      print >>sys.stderr, 'DEBUG: link: %s'%link
      rval=rest_utils.GetURL(base_uri+link,{},parse_json=True,verbose=verbose)
      expds=rval['collection']
    if not expds: break

    for expd in expds:
      n_expd+=1
      eid=int(expd['bardExptId'])
      sid=int(expd['sid'])
      cid=int(expd['cid'])
      outcome=int(expd['outcome'])
      fout.write('%d,%d,%d\n'%(eid,sid,outcome))
      fout.flush()

      n_sam_tst+=1

      if not eids.has_key(eid):
        eids[eid]=False
      if not cids.has_key(cid):
        cids[cid]=False
      if outcome==2:
        n_sam_act+=1
        eids[eid]=True
        cids[cid]=True

    if eids_query:
      nskip+=nchunk
    else:
      link=rval['link']
      if not link: break  ## END of expdata IDs

  for cid,act in cids.items():
    if act:
      n_cpd_act+=1
  for eid,act in eids.items():
    if act:
      n_exp_act+=1

  print >>sys.stderr, ('n_expd: %d'%n_expd)
  print >>sys.stderr, ('n_cpd_tst: %d'%len(cids))
  print >>sys.stderr, ('n_cpd_act: %d'%n_cpd_act)
  print >>sys.stderr, ('n_exp_tst: %d'%len(eids))
  print >>sys.stderr, ('n_exp_act: %d'%n_exp_act)
  print >>sys.stderr, ('n_sam_tst: %d'%n_sam_tst)
  print >>sys.stderr, ('n_sam_act: %d'%n_sam_act)

  return

#############################################################################
def ExpSummary(base_uri,fout,eid,verbose=0):
  rval=rest_utils.GetURL(base_uri+'/experiments/%d?expand=true'%eid,{},parse_json=True,verbose=verbose)
  return rval

#############################################################################
def EID2PCAID(base_uri,eid,verbose=0):
  rval=rest_utils.GetURL(base_uri+'/experiments/%d'%eid,{},parse_json=True,verbose=verbose)
  aid=None
  try:
    aid=rval['pubchemAid']
    aid=int(aid)
    return aid
  except Exception, e:
    print >>sys.stderr, 'Error (Exception): %s'%e
  return aid

#############################################################################
def EIDs2PCAIDs(base_uri,fout,eids_query,nskip,nmax,verbose=0):
  n_all=0; n_out=0; n_err=0;
  n_expd=0;
  fout.write('EID,PubchemAID\n')
  for eid in eids_query:
    n_expd+=1
    if nskip>=n_expd: continue
    aid=EID2PCAID(base_uri,eid,verbose)
    if not aid:
      n_err+=1
      aid=0
    fout.write('%d,%d\n'%(eid,aid))
    fout.flush()
    if verbose>1:
      print >>sys.stderr, 'EIDs2PCAIDs: %d -> %d'%(eid,aid)
    n_out+=1
  n_all=n_expd
  return n_all,n_out,n_err

#############################################################################
def Activity(base_uri,fout,sids_query,eids_query,nchunk=100,nskip_sub0=0,nskip_exp0=0,verbose=0):
  '''This important function obtains activity data via the /exptdata
  resource for a specified set of SIDs and EIDs.  The nchunk param
  specifies the size of the NxN submatrix of values obtained per request.
  Outer loop SIDs, inner loop EIDs.
'''
  fout.write('eid,sid,outcome\n')
  n_expd=0;
  n_cpd_act=0; n_sub_act=0; n_exp_act=0; 
  n_sam_act=0; n_sam_tst=0;
  n_err=0; n_err2=0;
  eids={}; sids={}; cids={};
  outcome_counts={};
  for i in range(6): outcome_counts[i]=0;
  nskip_sub=nskip_sub0
  t0=time.time()
  while True: ## process sids in chunks:
    #print >>sys.stderr, 'DEBUG: len(sids_query)=%d ; nskip_sub=%d'%(len(sids_query),nskip_sub)
    if len(sids_query)<=nskip_sub: break
    sidstr=(','.join(map(lambda x:str(x),sids_query[nskip_sub:nskip_sub+nchunk])))
    nskip_exp=nskip_exp0
    while True: ## process eids in chunks:
      #print >>sys.stderr, 'DEBUG: len(eids_query)=%d ; nskip_exp=%d'%(len(eids_query),nskip_exp)
      if len(eids_query)<=nskip_exp: break
      eidstr=(','.join(map(lambda x:str(x),eids_query[nskip_exp:nskip_exp+nchunk])))
      d={'sids':sidstr,'eids':eidstr}
      expds=rest_utils.PostURL(base_uri+'/exptdata',{},d,parse_json=True,verbose=verbose)
      if expds==None:
        n_err+=1
        if n_err%100==0:
          s_timeout=300
          print >>sys.stderr, 'DEBUG: (n_err=%d); RESTful timeout for server recovery (%d s)...'%(n_err,s_timeout)
          time.sleep(s_timeout)
        else:
          time.sleep(1)
        continue  ## try again, same data
      if type(expds) is not types.ListType:
        n_err+=1
        if n_err%100==0:
          s_timeout=300
          print >>sys.stderr, 'DEBUG: bad exptdata (n_err=%d): "%s"; RESTful timeout for server recovery (%d s)...'%(n_err,str(expds),s_timeout)
          time.sleep(s_timeout)
        else:
          time.sleep(1)
        continue
      for expd in expds:
        if type(expd) is not types.DictType:
          n_err2+=1
          if n_err2%100==0:
            print >>sys.stderr, 'DEBUG: bad exptdatum (n_err2=%d): "%s"'%(n_err2,str(expd))
          else:
            time.sleep(1)
          continue
        n_expd+=1
        try:
          eid=int(expd['bardExptId'])
          sid=int(expd['sid'])
          cid=int(expd['cid'])
        except Exception, e:
          print >>sys.stderr, 'Error (Exception): %s'%e
          continue

        outcome=Expd2PCOutcome(expd)

        fout.write('%d,%d,%d\n'%(eid,sid,outcome))
        fout.flush()
        n_sam_tst+=1
        if not eids.has_key(eid):
          eids[eid]=False
        if not cids.has_key(cid):
          cids[cid]=False
        if not sids.has_key(sid):
          sids[sid]=False
        if outcome==2:
          n_sam_act+=1
          eids[eid]=True
          cids[cid]=True
          sids[sid]=True
        outcome_counts[outcome]+=1
      if verbose>1:
        print >>sys.stderr, 'sids[%d-%d]'%(nskip_sub+1,min(nskip_sub+nchunk,len(sids_query))),
        print >>sys.stderr, 'eids[%d-%d]'%(nskip_exp+1,min(nskip_exp+nchunk,len(eids_query))),
        print >>sys.stderr, 'n:%d out:%d'%(n_expd,n_sam_tst),
        print >>sys.stderr, 'tod: %s elapsed: %s'%(time.strftime('%Hh:%Mm:%Ss',time.localtime()),time_utils.NiceTime(time.time()-t0))
      nskip_exp+=nchunk
    nskip_sub+=nchunk
  for cid,act in cids.items():
    if act: n_cpd_act+=1
  for eid,act in eids.items():
    if act: n_exp_act+=1
  for sid,act in sids.items():
    if act: n_sub_act+=1

  if verbose>0:
    print >>sys.stderr, 'sids: %d'%(len(sids_query)),
    print >>sys.stderr, 'eids: %d'%(len(eids_query)),
    print >>sys.stderr, ('n_expd: %d'%n_expd)
    print >>sys.stderr, ('n_exp_tst: %d'%len(eids))
    print >>sys.stderr, ('n_exp_act: %d'%n_exp_act)
    print >>sys.stderr, ('n_cpd_tst: %d'%len(cids))
    print >>sys.stderr, ('n_cpd_act: %d'%n_cpd_act)
    print >>sys.stderr, ('n_sub_tst: %d'%len(sids))
    print >>sys.stderr, ('n_sub_act: %d'%n_sub_act)
    print >>sys.stderr, ('n_sam_tst: %d'%n_sam_tst)
    print >>sys.stderr, ('n_sam_act: %d'%n_sam_act)
    for i in range(6):
      print >>sys.stderr, ('outcome_counts[%d]: %8d'%(i,outcome_counts[i]))
    print >>sys.stderr, 'elapsed: %s'%time_utils.NiceTime(time.time()-t0)

  return

#############################################################################
###   1 = inactive
###   2 = active
###   3 = inconclusive
###   4 = unspecified
###   5 = probe
#############################################################################
def Expd2PCOutcome(expd):
  outcome=0; #default
  if expd.has_key('rootElements') and len(expd['rootElements'])>0:
    ok=False;
    for element in expd['rootElements']:
      if element.has_key('displayName') and element['displayName']=='PubChem outcome':
        if element.has_key('value'):
          if element['value']=='Inactive':
            outcome=1
          elif element['value']=='Active':
            outcome=2
          elif element['value']=='Inconclusive':
            outcome=3
          elif element['value']=='Unspecified':
            outcome=4
          elif element['value']=='Probe':
            outcome=5
          ok=True
          break
    if not ok:
      outcome=0
      print >>sys.stderr, 'DEBUG: expd format error, no PubChem outcome value.'
      #print >>sys.stderr, 'DEBUG: expd: %s'%json.dumps(expd,sort_keys=True,indent=2)
      #sys.exit() #DEBUG

  else:
    outcome=0
    print >>sys.stderr, 'DEBUG: expd format error, no or empty rootElements.'
    #print >>sys.stderr, 'DEBUG: expd: %s'%json.dumps(expd,sort_keys=True,indent=2)
    #sys.exit() #DEBUG
    
  return outcome

#############################################################################
def CpdActivity(base_uri,fout,cid,verbose=0):
  tags=['bardExptId','sid','cid','outcome']  ## mucho data; minimize storage
  fout.write('%s\n'%(','.join(tags)))
  n_expd=0; n_sub_act=0; n_exp_act=0;
  n_sam_act=0; n_sam_tst=0;
  eids={}; sids={};
  link=('/compounds/%d/exptdata'%cid)
  while True:
    rval=rest_utils.GetURL(base_uri+link,{},parse_json=True,verbose=verbose)
    expdlinks=rval['collection']
    if not expdlinks or len(expdlinks)==0: break
    for expdlink in expdlinks:
      expd=rest_utils.GetURL(base_uri+expdlink,{},parse_json=True,verbose=verbose)
      if not expd: break
      n_expd+=1
      eid=int(expd['bardExptId'])
      sid=int(expd['sid'])
      cid=int(expd['cid'])
      outcome=Expd2PCOutcome(expd)

      fout.write('%d,%d,%d,%d\n'%(eid,sid,cid,outcome))
      fout.flush()

      n_sam_tst+=1
      if not sids.has_key(sid):
        sids[sid]=False
      if not eids.has_key(eid):
        eids[eid]=False
      if outcome==2:
        n_sam_act+=1
        eids[eid]=True
        sids[sid]=True
    link=rval['link']
    if not link: break  ## END of expdata IDs
  for eid,act in eids.items():
    if act:
      n_exp_act+=1
  for sid,act in sids.items():
    if act:
      n_sub_act+=1

  print >>sys.stderr, 'n_expd: %d'%n_expd
  print >>sys.stderr, 'n_exp_tst: %d'%len(eids)
  print >>sys.stderr, 'n_exp_act: %d'%n_exp_act
  print >>sys.stderr, 'n_sub_tst: %d'%n_sub_tst
  print >>sys.stderr, 'n_sub_act: %d'%len(sids)
  print >>sys.stderr, 'n_sam_tst: %d'%n_sam_tst
  print >>sys.stderr, 'n_sam_act: %d'%n_sam_act

  return

#############################################################################
def SearchProjects(base_uri,qstr,verbose=0):
  print rest_utils.GetURL(base_uri+'/projects/_count?filter=%s[description]'%qstr,{},parse_json=True,verbose=verbose)
  return

##############################################################################
def ListPlugins(base_uri,fout,verbose=0):
  plugins = rest_utils.GetURL(base_uri+'/plugins/registry/list',{},parse_json=True,verbose=verbose)
  fout.write('%18s\t%-8s\t%-9s\t%s\n'%('name','version','available','title'))
  for plugin in plugins:
    name = os.path.basename(plugin['path'])
    title = plugin['title']
    version = plugin['version']
    available = plugin['available']
    fout.write('%18s\t%-8s\t%-9s\t%s\n'%(name,version,available,title))
    fout.flush()
  return

#############################################################################
