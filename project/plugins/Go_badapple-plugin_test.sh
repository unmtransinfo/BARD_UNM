#!/bin/sh
#
#set -x
#
#API_HOST="bard.nih.gov"
API_HOST="localhost"
#
#API_BASEPATH='/api/v1/plugins'
#API_BASEPATH='/api/latest/plugins'
#API_BASEPATH='/api/straw/plugins'
#
#API_BASEPATH="/tomcat/bardplugin_badapple"
API_BASEPATH="/tomcat/bardplugins"
#
BASE_URL="http://${API_HOST}${API_BASEPATH}"
#
#"${BASE_URL}/registry/list" \
#
for url in \
"${BASE_URL}/badapple/_info" \
"${BASE_URL}/badapple/description" \
"${BASE_URL}/badapple/prom/scafid/4" \
"${BASE_URL}/badapple/prom/scafid/4?expand=true" \
"${BASE_URL}/badapple/prom/cid/752424" \
"${BASE_URL}/badapple/prom/cid/752424?expand=true" \
"${BASE_URL}/badapple/prom/cid/752424?expand=true&pretty=true" \
"${BASE_URL}/badapple/prom/analyze?smiles=COc1cc2c(ccnc2cc1)C(O)C4CC(CC3)C(C=C)CN34&pretty=true" \
	; do
	rest_request.py --url "$url"
done
#
