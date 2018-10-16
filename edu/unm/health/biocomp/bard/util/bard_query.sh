#!/bin/sh
#
#
LIBDIR=$HOME/src/java/lib
CLASSPATH=$LIBDIR/unm_biocomp_bard.jar
CLASSPATH=$CLASSPATH:$LIBDIR/unm_biocomp_util.jar
#CLASSPATH=$CLASSPATH:/home/app/ChemAxon/JChem/lib/jchem.jar
APPDIR=/home/app
CLASSPATH=$CLASSPATH:$APPDIR/lib/org.json.jar
#
PROG=edu.unm.health.biocomp.bard.util.bard_query
echo $PROG
#
java -classpath $CLASSPATH $PROG $*
#
