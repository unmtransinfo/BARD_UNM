#!/usr/bin/env python
#############################################################################
### barddb_query.py - BARD warehouse query application.  "BardDB" refers
### to the MySql DB accessed via SQL (not the REST API).
###
### Jeremy Yang
###  1 Aug 2014
#############################################################################
import os,sys,re,getopt,cgi
import MySQLdb
import barddb_utils

PROG=os.path.basename(sys.argv[0])

DBHOST="habanero";
DBNAME="bard";
DBUSR="bard";
DBPW="sAlUd";

#############################################################################
USAGE='''\
%(PROG)s
required (one of):
        --counts
        --describe
        --list_projects
options:
        --o OFILE ............... output (XGMML|CSV)
        --dbhost DBHOST ......... [%(DBHOST)s]
        --dbname DBNAME ......... [%(DBNAME)s]
        --dbusr DBUSR ........... [%(DBUSR)s]
        --dbpw DBPW ............. 
        --v ..................... verbose
        --h ..................... this help
'''%{'PROG':PROG, 'DBHOST':DBHOST, 'DBNAME':DBNAME, 'DBUSR':DBUSR }

#############################################################################
if __name__=='__main__':
  def ErrorExit(msg):
    print >>sys.stderr,msg
    sys.exit(1)

  status=False; counts=False; describe=False;
  list_projects=False; 
  ofile='';
  verbose=0;
  opts,pargs=getopt.getopt(sys.argv[1:],'',['o=',
    'dbhost=','dbname=','dbusr=','dbpw=',
    'list_projects',
    'counts','status','describe','h=','help','v','vv'])
  if not opts: ErrorExit(USAGE)
  for (opt,val) in opts:
    if opt=='--help': ErrorExit(USAGE)
    elif opt=='--o': ofile=val
    elif opt=='--dbhost': DBHOST=val
    elif opt=='--dbname': DBNAME=val
    elif opt=='--dbusr': DBUSR=val
    elif opt=='--dbpw': DBPW=val
    elif opt=='--status': status=True
    elif opt=='--counts': counts=True
    elif opt=='--describe': describe=True
    elif opt=='--list_projects': list_projects=True
    elif opt=='--v': verbose=1
    elif opt=='--vv': verbose=2
    else: ErrorExit('Illegal option: %s'%(opt)+USAGE)

  if ofile:
    fout=open(ofile,'w+')
    if not fout:
      ErrorExit('Could not open output file: %s'%fpath)
  else:
    fout=sys.stdout

  db=MySQLdb.connect(host=DBHOST,user=DBUSR,passwd=DBPW,db=DBNAME)

  if verbose:
    print >>sys.stderr, "DB: %s@%s"%(DBNAME,DBHOST)

  if status:
    barddb_utils.DBStatus(db)
  elif counts:
    barddb_utils.TableCounts(db)
  elif describe:
    barddb_utils.DBDescribe(db)
  elif list_projects:
    barddb_utils.ListProjects(db)
  else:
    ErrorExit('No operation specified.\n'+USAGE)
