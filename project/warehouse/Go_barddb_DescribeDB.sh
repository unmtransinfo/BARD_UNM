#!/bin/sh
#
set -e
#
DBHOST=habanero.health.unm.edu
DBNAME=bard
USR=bard
PW=sAlUd
#
TABLES="\
bard_access_log \
bard_assay \
bard_biology \
bard_experiment \
bard_experiment_data \
bard_experiment_result \
bard_panel_assay \
bard_project \
bard_project_experiment \
bard_update_time \
cap_annotation \
cap_assay \
cap_dict_elem \
cap_dict_obj \
cap_document \
cap_expt_result \
cap_project_annotation \
cid_sid \
compound \
compound_fp \
compound_molfile \
compound_props \
compound_rank \
etag \
substance \
synonyms"
#
mysql -h $DBHOST -u $USR -p$PW -tv ${DBNAME} <<__EOF__
SELECT NOW(),USER(),VERSION();
SHOW DATABASES;
SHOW TABLES;
__EOF__
#
for table in $TABLES ; do
	mysql -h $DBHOST -u $USR -p$PW -tv ${DBNAME} <<__EOF__
SELECT COUNT(*) AS '${table}_RECORD_COUNT' FROM ${table} ;
DESCRIBE ${table} ;
__EOF__
	echo
done
#
