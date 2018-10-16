#!/bin/sh
#
set -x
#
DBNAME='nci'
TMPDIR=$HOME/../jjbigdata/nci/TMP
#
files="${DBNAME}.sql.gz \
${DBNAME}.names.sql \
${DBNAME}.sdf2d.sql \
${DBNAME}.sdf3d.sql \
${DBNAME}.fp164.sql \
${DBNAME}.fp320.sql"
#
for file in $files ; do
	if [ -f $TMPDIR/$file ]; then
		rm $TMPDIR/$file
	fi
done
