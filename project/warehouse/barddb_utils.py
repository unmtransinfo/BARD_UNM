#!/usr/bin/env python
#############################################################################
### barddb_utils.py - Utility functions for accessing the BARD
### MySql warehouse.
#############################################################################
### SELECT NOW(),USER(),VERSION();
### SHOW DATABASES;
### SHOW TABLES;
### DESCRIBE tablename;
### SHOW INDEX FROM tablename;
### GRANT ALL ON *.* TO jjyang@localhost;
### GRANT ALL ON *.* TO jjyang@grants;
### GRANT ALL ON *.* TO jjyang@grants.health.unm.edu;
### GRANT SELECT ON bard.* to http@localhost IDENTIFIED BY "foobar";
### GRANT SELECT ON bard.* to http@grants IDENTIFIED BY "foobar";
### GRANT SELECT ON bard.* to http@grants.health.unm.edu IDENTIFIED BY "foobar";
#############################################################################
### MySQLdb Python package:
### 
### (Must get setuptools from python.org.
### sudo sh setuptools-0.6c5-py2.3.egg)
### 
### MySQLdb:
### $ tar xfz MySQL-python-1.2.1.tar.gz
### $ cd MySQL-python-1.2.1
### $ # edit site.cfg if necessary
### $ python setup.py build
### $ sudo python setup.py install # or su first
#############################################################################
### Jeremy Yang
###  1 Aug 2014
#############################################################################
import MySQLdb,types

#############################################################################
def DBStatus(db):
  c=db.cursor()
  c.execute('SHOW STATUS')
  lines=c.fetchall()
  for line in lines:
    print line

#############################################################################
def TableCounts(db):
  c=db.cursor()
  c.execute('SHOW TABLES')
  #c.execute('SELECT TABLE_NAME FROM public.INFORMATION_SCHEMA')
  tables=c.fetchall()
  for table in tables:
    c.execute('SELECT COUNT(*) FROM '+table[0])
    fields=c.fetchone()
    if not fields: break
    nrows=fields[0]
    if type(table) in (types.TupleType,types.ListType):
      print "%24s (%12d rows)"%(table[0],nrows)
    else:
      print "%24s (%12d rows)"%(table,nrows)
  c.close()

#############################################################################
def DBDescribe(db):
  c=db.cursor()
  c.execute('SHOW TABLES')
  tables=c.fetchall()
  for table in tables:
    desc_table=[]
    print "-"*100
    print "%s:"%table
    c.execute('DESCRIBE '+table[0])
    while True:
      fields = c.fetchone()
      if not fields: break
      desc_table.append(fields[:2])
    PrintTable(desc_table)
  print "-"*100
  c.close()

#############################################################################
def PrintTable(t):
  for row in t:
    for cell in row:
      print "%24s" % cell,
    print

#############################################################################
def PrintTableDict(t):
  for row in t:
    for tag,cell in row.items():
      print "%s: %s   " % (tag,cell),
    print

#############################################################################
def ShowTables(db):
  c = db.cursor(MySQLdb.cursors.DictCursor)
  c.execute('SHOW TABLES')
  tables=c.fetchall()
  for table in tables:
    desc_table=[]
    for tag,tablename in table.items():
      print "%s: %s" % (tag,tablename)
    c.execute('DESCRIBE '+tablename)
    while True:
      fields = c.fetchone()
      if not fields: break
      desc_table.append(fields)
    PrintTableDict(desc_table)
  c.close()

#############################################################################
def ListProjects(db):
  c=db.cursor()
  c.execute('SELECT bard_proj_id,name FROM bard_project')
  projects=c.fetchall()
  for pid,pname in projects:
    print "%d:\t%s"%(pid,pname)
  c.close()

#############################################################################
