#!/usr/bin/env python
##############################################################################
### bard_query.py - query via REST API
###
###
### Jeremy Yang
### 23 Jul 2014
##############################################################################
import sys,os,re,getopt,time,json
import codecs,encodings
#
import time_utils
import bard_api_utils
#
PROG=os.path.basename(sys.argv[0])
#
API_HOST='bard.nih.gov'
#API_VERSION='latest'
API_VERSION='straw'
API_BASE_PATH='/api'
#
##############################################################################
if __name__=='__main__':
  nchunk=100;
  usage='''\
%(PROG)s - BARD REST API utility client
required (one of):
        --counts ........................ resource _count's
        --describe ...................... describe API (_info's)
        --list_projects ................. list projects
        --list_assays ................... list assays
        --list_experiments .............. list experiments
	--list_subs_source .............. list substances of given SOURCE
	--list_subs_tested .............. list substances which are tested
	--list_cpds_source .............. list compounds of given SOURCE
	--list_cpds_tested .............. list compounds which are tested
        --list_targets .................. list ALL targets (inc. all Uniprot)
        --list_genes .................... list biology entities, type: GENE
        --list_proteins ................. list biology entities, type: PROTEIN 
        --list_proteins_classified ...... list biology entities, type: PROTEIN, Panther-classified
        --list_processes ................ list biology entities, type: PROCESS
        --list_biologys ................. list biology entities, all types
        --list_biologytypes ............. list biology types 
        --list_biologycounts ............ list biology counts 
        --list_probes ................... list probe compounds
        --list_plugins .................. 
        --get_cpdactivity ............... activity for compound (CID)
        --get_expactivity ............... activity for experiment (EID)
        --get_expsummary ................ summary for experiment (EID)
        --get_subactivity ............... activity for substance (SID)
        --get_activity .................. activity for SIDs + EIDs (Badapple)
        --get_cpdsmi .................... get smiles for CID
	--get_pcaids .................... lookup Pubchem AIDs for input BARD EIDs
parameters:
	--sid SID ....................... 
	--cid CID ....................... 
	--eid EID ....................... 
	--sidfile FILE .................. query SIDs
	--eidfile FILE .................. query EIDs
	--source SOURCE ................. query SOURCE
options:
	--o OFILE ....................... output file (normally CSV)
	--nskip NSKIP ................... 
	--nskip_sub NSKIP ............... for --get_activity
	--nskip_exp NSKIP ............... for --get_activity
	--nmax NMAX ..................... 
	--nmin_cpd NMIN ................. for --list_experiments
	--nchunk NCHUNK ................. [%(NCHUNK)d]
	--api_host HOST ................. [%(API_HOST)s]
	--api_version VERSION ........... [%(API_VERSION)s]
	--api_base_path BASE_PATH ....... [%(API_BASE_PATH)s]
        --v[v[v]] ....................... verbose [very [very]]
        --h ............................. this help
'''%{'PROG':PROG,'API_HOST':API_HOST,'API_VERSION':API_VERSION,'API_BASE_PATH':API_BASE_PATH,'NCHUNK':nchunk}

  def ErrorExit(msg):
    print >>sys.stderr,msg
    sys.exit(1)

  nskip=0; nskip_sub=0; nskip_exp=0; nmax=0; nmin_cpd=0;
  ofile=None; verbose=0;
  counts=False; describe=False;
  cpd_smi2id=''; get_cpdsmi=False;
  scaf_smi2id=''; scafsmi=None;
  get_cpdactivity=False;
  get_expactivity=False;
  get_expsummary=False;
  get_subactivity=False;
  get_activity=False;
  list_projects=False;
  list_projects_unm=False;
  list_targets=False;
  list_biologycounts=False;
  list_biologytypes=False;
  list_biologys=False;
  list_genes=False;
  list_proteins=False;
  list_proteins_classified=False;
  list_processes=False;
  list_assays=False;
  list_probes=False;
  list_assays_unm=False;
  list_experiments=False;
  list_experiments_unm=False;
  list_experiments_hts=False;
  list_subs_tested=False;
  list_subs_source=False;
  list_cpds_tested=False;
  list_cpds_source=False;
  get_pcaids=False;
  list_plugins=False;
  source=None;
  sidfile=None; eidfile=None;
  cid=None; eid=None; sid=None; id_query=None;
  opts,pargs=getopt.getopt(sys.argv[1:],'',['o=',
    'list_projects',
    'list_projects_unm',
    'list_targets',
    'list_genes',
    'list_proteins',
    'list_proteins_classified',
    'list_processes',
    'list_biologycounts',
    'list_biologytypes',
    'list_biologys',
    'list_assays',
    'list_assays_unm',
    'list_experiments',
    'list_experiments_unm',
    'list_experiments_hts',
    'list_subs_tested',
    'list_subs_source',
    'list_cpds_tested',
    'list_cpds_source',
    'source=',
    'list_probes',
    'get_cpdsmi',
    'cpd_smi2id=',
    'scafsmi=',
    'scaf_smi2id=',
    'get_cpdactivity',
    'get_expactivity',
    'get_expsummary',
    'get_subactivity',
    'get_activity',
    'get_pcaids',
    'list_plugins',
    'id=', 'cid=', 'sid=', 'eid=',
    'sidfile=',
    'eidfile=',
    'nskip=','nskip_sub=','nskip_exp=','nmax=','nchunk=',
    'nmin_cpd=',
    'counts','describe',
    'api_version=',
    'api_host=',
    'api_base_path=',
    'help','v','vv','vvv'])
  if not opts: ErrorExit(usage)
  for (opt,val) in opts:
    if opt=='--help': ErrorExit(usage)
    elif opt=='--o': ofile=val
    elif opt=='--counts': counts=True
    elif opt=='--describe': describe=True
    elif opt=='--list_projects': list_projects=True
    elif opt=='--list_projects_unm': list_projects_unm=True
    elif opt=='--list_tarlist_s': list_targets=True
    elif opt=='--list_genes': list_genes=True
    elif opt=='--list_proteins': list_proteins=True
    elif opt=='--list_proteins_classified': list_proteins_classified=True
    elif opt=='--list_processes': list_processes=True
    elif opt=='--list_biologycounts': list_biologycounts=True
    elif opt=='--list_biologytypes': list_biologytypes=True
    elif opt=='--list_biologys': list_biologys=True
    elif opt=='--list_assays': list_assays=True
    elif opt=='--list_assays_unm': list_assays_unm=True
    elif opt=='--list_experiments': list_experiments=True
    elif opt=='--list_experiments_unm': list_experiments_unm=True
    elif opt=='--list_experiments_hts': list_experiments_hts=True
    elif opt=='--list_subs_tested': list_subs_tested=True
    elif opt=='--list_cpds_tested': list_cpds_tested=True
    elif opt=='--list_subs_source': list_subs_source=True
    elif opt=='--list_cpds_source': list_cpds_source=True
    elif opt=='--source': source=val
    elif opt=='--list_probes': list_probes=True
    elif opt=='--list_plugins': list_plugins=True
    elif opt=='--cpd_smi2id': cpd_smi2id=val
    elif opt=='--scaf_smi2id': scaf_smi2id=val
    elif opt=='--get_cpdsmi': get_cpdsmi=True
    elif opt=='--id': id_query=int(val)
    elif opt=='--cid': cid=int(val)
    elif opt=='--sid': sid=int(val)
    elif opt=='--eid': eid=int(val)
    elif opt=='--get_cpdactivity': get_cpdactivity=True
    elif opt=='--get_expactivity': get_expactivity=True
    elif opt=='--get_expsummary': get_expsummary=True
    elif opt=='--get_subactivity': get_subactivity=True
    elif opt=='--get_activity': get_activity=True
    elif opt=='--get_pcaids': get_pcaids=True
    elif opt=='--sidfile': sidfile=val
    elif opt=='--eidfile': eidfile=val
    elif opt=='--scaf_id2smi': scaf_id2smi=int(val)
    elif opt=='--nskip': nskip=int(val)
    elif opt=='--nskip_sub': nskip_sub=int(val)
    elif opt=='--nskip_exp': nskip_exp=int(val)
    elif opt=='--nmax': nmax=int(val)
    elif opt=='--nmin_cpd': nmin_cpd=int(val)
    elif opt=='--nchunk': nchunk=int(val)
    elif opt=='--api_version': API_VERSION=val
    elif opt=='--api_host': API_HOST=val
    elif opt=='--api_base_path': API_BASE_PATH=val
    elif opt=='--v': verbose=1
    elif opt=='--vv': verbose=2
    elif opt=='--vvv': verbose=3
    else: ErrorExit('Illegal option: %s\n%s'%(opt,usage))

  BASE_URI='http://'+API_HOST+API_BASE_PATH+'/'+API_VERSION

  if ofile:
    #fout=open(ofile,"w+")
    fout=codecs.open(ofile,"w","utf8","replace")
    if not fout: ErrorExit('ERROR: cannot open outfile: %s'%ofile)
  else:
    #fout=sys.stdout
    fout=codecs.getwriter('utf8')(sys.stdout,errors="replace")

  sids=[]
  if sidfile:
    fin=open(sidfile)
    if not fin: ErrorExit('ERROR: cannot open sidfile: %s'%sidfile)
    while True:
      line=fin.readline()
      if not line: break
      try:
        sids.append(int(line.rstrip()))
      except:
        print >>sys.stderr, 'ERROR: bad input SID: %s'%line
        continue
    if verbose:
      print >>sys.stderr, '%s: input SIDs: %d'%(PROG,len(sids))
    fin.close()
  eids=[]
  if eidfile:
    fin=open(eidfile)
    if not fin: ErrorExit('ERROR: cannot open eidfile: %s'%eidfile)
    while True:
      line=fin.readline()
      if not line: break
      try:
        eids.append(int(line.rstrip()))
      except:
        print >>sys.stderr, 'ERROR: bad input EID: %s'%line
        continue
    if verbose:
      print >>sys.stderr, '%s: input EIDs: %d'%(PROG,len(eids))
    fin.close()

  if verbose:
    print >>sys.stderr, '%s: BASE_URI: %s'%(PROG,BASE_URI)
    print >>sys.stderr, '%s: %s'%(PROG,time.asctime())
  t0=time.time()

  if describe:
    print bard_api_utils.Describe(BASE_URI,verbose)

  elif counts:
    print bard_api_utils.Counts(BASE_URI,verbose)

  elif get_cpdsmi:
    if not cid: ErrorExit('ERROR: requires --cid.')
    print bard_api_utils.Cid2Smiles(BASE_URI,cid,verbose)

  elif get_cpdactivity:
    if not cid: ErrorExit('ERROR: requires --cid.')
    bard_api_utils.CpdActivity(BASE_URI,fout,cid,verbose)

  elif get_subactivity:
    if not (sid and eids): ErrorExit('ERROR: requires --eidfile and --sid')
    bard_api_utils.SubActivity(BASE_URI,fout,sid,eids,verbose)

  elif get_expactivity:
    if not (sids and eid): ErrorExit('ERROR: requires --sidfile and --eid')
    bard_api_utils.ExpActivity(BASE_URI,fout,eid,sids,verbose)

  elif get_expsummary:
    if not eid: ErrorExit('ERROR: requires --eid')
    rval=bard_api_utils.ExpSummary(BASE_URI,fout,eid,verbose)
    print json.dumps(rval,indent=2)

  elif get_activity:
    if not (sids and eids): ErrorExit('ERROR: --activity requires --eidfile and --sidfile')
    bard_api_utils.Activity(BASE_URI,fout,sids,eids,nchunk,nskip_sub,nskip_exp,verbose)

  elif list_projects:
    n_all,n_out,n_err = bard_api_utils.ListProjects(BASE_URI,fout,verbose)
    print >>sys.stderr, '%s: n_all: %d  n_out: %d  n_err: %d'%(PROG,n_all,n_out,n_err)

  elif list_projects_unm:
    n_all,n_out,n_err = bard_api_utils.ListProjects_UNM(BASE_URI,fout,verbose)
    print >>sys.stderr, '%s: n_all: %d  n_out: %d  n_err: %d'%(PROG,n_all,n_out,n_err)

  elif list_targets:
    n_all,n_out,n_err = bard_api_utils.ListTargets(BASE_URI,fout,verbose)
    print >>sys.stderr, '%s: n_all: %d  n_out: %d  n_err: %d'%(PROG,n_all,n_out,n_err)

  elif list_genes:
    n_all,n_out,n_err = bard_api_utils.ListGenes(BASE_URI,fout,verbose)
    print >>sys.stderr, '%s: n_all: %d  n_out: %d  n_err: %d'%(PROG,n_all,n_out,n_err)

  elif list_proteins:
    n_all,n_out,n_err = bard_api_utils.ListProteins(BASE_URI,fout,verbose)
    print >>sys.stderr, '%s: n_all: %d  n_out: %d  n_err: %d'%(PROG,n_all,n_out,n_err)

  elif list_proteins_classified:
    n_all,n_out,n_err = bard_api_utils.ListProteins_classified(BASE_URI,fout,verbose)
    print >>sys.stderr, '%s: n_all: %d  n_out: %d  n_err: %d'%(PROG,n_all,n_out,n_err)

  elif list_processes:
    n_all,n_out,n_err = bard_api_utils.ListProcesses(BASE_URI,fout,verbose)
    print >>sys.stderr, '%s: n_all: %d  n_out: %d  n_err: %d'%(PROG,n_all,n_out,n_err)

  elif list_biologycounts:
    bard_api_utils.ListBiologyCounts(BASE_URI,verbose)

  elif list_biologytypes:
    btypes=bard_api_utils.ListBiologyTypes(BASE_URI,verbose)
    for btype in btypes: print btype

  elif list_biologys:
    n_all,n_out,n_err = bard_api_utils.ListBiologys(BASE_URI,fout,verbose)
    print >>sys.stderr, '%s: n_all: %d  n_out: %d  n_err: %d'%(PROG,n_all,n_out,n_err)

  elif list_assays:
    n_all,n_out,n_err = bard_api_utils.ListAssays(BASE_URI,fout,verbose)
    print >>sys.stderr, '%s: n_all: %d  n_out: %d  n_err: %d'%(PROG,n_all,n_out,n_err)

### NOT YET IMPLEMENTED:
# elif list_assays_unm:
#   n_all,n_out,n_err = bard_api_utils.ListAssays_UNM(BASE_URI,fout,verbose)
#   print >>sys.stderr, '%s: n_all: %d  n_out: %d  n_err: %d'%(PROG,n_all,n_out,n_err)

  elif list_experiments:
    n_all,n_out,n_err = bard_api_utils.ListExperiments(BASE_URI,fout,nmin_cpd,verbose)
    print >>sys.stderr, '%s: n_all: %d  n_out: %d  n_err: %d'%(PROG,n_all,n_out,n_err)

# elif list_experiments_hts:
#   mincpds=20000
#   n_all,n_out,n_err = bard_api_utils.ListExperiments(BASE_URI,fout,mincpds,verbose)
#   print >>sys.stderr, '%s: n_all: %d  n_out: %d  n_err: %d'%(PROG,n_all,n_out,n_err)

### NOT YET IMPLEMENTED:
# elif list_experiments_unm:
#   mincpds=0
#   n_all,n_out,n_err = bard_api_utils.ListExperiments_UNM(BASE_URI,fout,mincpds,verbose)
#   print >>sys.stderr, '%s: n_all: %d  n_out: %d  n_err: %d'%(PROG,n_all,n_out,n_err)

  elif list_subs_tested:
    n_all,n_out,n_err = bard_api_utils.ListSubstancesTested(BASE_URI,fout,nskip,nmax,nchunk,verbose)
    print >>sys.stderr, '%s: n_all: %d  n_out: %d  n_err: %d'%(PROG,n_all,n_out,n_err)

  elif list_cpds_tested:
    n_all,n_out,n_err = bard_api_utils.ListCompoundsTested(BASE_URI,fout,nskip,nmax,nchunk,verbose)
    print >>sys.stderr, '%s: n_all: %d  n_out: %d  n_err: %d'%(PROG,n_all,n_out,n_err)

  elif list_subs_source:
    if not source: ErrorExit('ERROR: requires --source.')
    n_all,n_out,n_err = bard_api_utils.ListSubstancesFromSource(BASE_URI,fout,source,nskip_sub,nmax,nchunk,verbose)
    print >>sys.stderr, '%s: n_all: %d  n_out: %d  n_err: %d'%(PROG,n_all,n_out,n_err)

  elif list_cpds_source:
    if not source: ErrorExit('ERROR: requires --source.')
    n_all,n_out,n_err = bard_api_utils.ListCompoundsFromSource(BASE_URI,fout,source,nskip,nmax,nchunk,verbose)
    print >>sys.stderr, '%s: n_all: %d  n_out: %d  n_err: %d'%(PROG,n_all,n_out,n_err)

  elif list_probes:
    n_all,n_out,n_err = bard_api_utils.ListProbes(BASE_URI,fout,verbose)
    print >>sys.stderr, '%s: n_all: %d  n_out: %d  n_err: %d'%(PROG,n_all,n_out,n_err)

  elif get_pcaids:
    if not eids: ErrorExit('ERROR: requires --eidfile.')
    n_all,n_out,n_err = bard_api_utils.EIDs2PCAIDs(BASE_URI,fout,eids,nskip_exp,nmax,verbose)
    print >>sys.stderr, '%s: n_all: %d  n_out: %d  n_err: %d'%(PROG,n_all,n_out,n_err)
  elif list_plugins:
      bard_api_utils.ListPlugins(BASE_URI,fout,verbose)
  else:
    ErrorExit('ERROR: no operation specified.\n%s'%(usage))

  if ofile:
    fout.close()

  if verbose:
    print >>sys.stderr, '%s: output file: %s'%(PROG,fout.name)
    print >>sys.stderr, '%s: elapsed: %s'%(PROG,time_utils.NiceTime(time.time()-t0))
    print >>sys.stderr, '%s: %s'%(PROG,time.asctime())
