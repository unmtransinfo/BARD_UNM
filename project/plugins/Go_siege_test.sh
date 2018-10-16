#!/bin/sh
##############################################################################
### Siege testing of specified web resource.
### 
### Jeremy Yang
###  6 Feb 2014
##############################################################################
#
if [ $# = 0 ]; then
	printf "%s - Siege test of BARD plugin\n" `basename $0`
	printf "ERROR: syntax: %s <plugin_name>\n" `basename $0`
	exit
fi
#
CWD=`pwd`
#
PLUGIN_NAME=$1
printf "PLUGIN_NAME: %s\n" $PLUGIN_NAME
#
URL_FILE="$CWD/urls/urls_${PLUGIN_NAME}.txt"
printf "URL_FILE: %s\n" $URL_FILE
#
REPS=1000
#
set -x
#
siege \
	--rc=$CWD/.siegerc \
	--file=$URL_FILE \
	--log=$CWD/data/siege_${PLUGIN_NAME}.log \
	--reps=$REPS
#
