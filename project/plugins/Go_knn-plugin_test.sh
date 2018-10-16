#!/bin/sh
#
#set -x
#
#BASE_URI='http://localhost:8080/bardplugins'
#BASE_URI='http://chiltepin.health.unm.edu/tomcat'
#BASE_URI='http://10.234.1.74:8080'
BASE_URI='http://bard.nih.gov/api/latest'
#BASE_URI='http://bard.nih.gov/api/straw'
#
#plugins:
# bard-api examples:
for url in \
"${BASE_URI}/plugins/knnbioactivity/_info" \
"${BASE_URI}/plugins/knnbioactivity/_count" \
"${BASE_URI}/plugins/knnbioactivity/cid/72279" \
"${BASE_URI}/plugins/knnbioactivity/sid/10321987" \
	; do
	rest_request.py --url "$url"
done
#
#
