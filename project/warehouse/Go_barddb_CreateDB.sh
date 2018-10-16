#!/bin/sh
#
set -e
set -x
#
DBNAME='bard'
DATADIR=/home/data/bard
TMPDIR=./data
#
mysql -v <<__EOF__
CREATE DATABASE $DBNAME;
USE $DBNAME;
__EOF__
#
gunzip -c $DATADIR/bard.sql.gz \
	| mysql $DBNAME
#
mysql $DBNAME <<__EOF__
RENAME TABLE compound_no_molfile TO compound ;
__EOF__
#
gunzip -c $DATADIR/bard_assay_data_no_big_blob_populated.sql.gz \
	| mysql $DBNAME
#
mysql $DBNAME <<__EOF__
RENAME TABLE assay_data_no_data_blob TO assay_data ;
__EOF__
#
