#!/bin/sh
#
#set -x
#
#BASE_URI='http://localhost:8080/bardplugins'
#BASE_URI='http://assay.nih.gov/bard/rest/v1'
#BASE_URI='http://bard.nih.gov/api/v1'
BASE_URI='http://bard.nih.gov/api/latest'
#BASE_URI='http://bard.nih.gov/api/straw'
#
#plugins:
# bard-api examples:
for uri in \
"${BASE_URI}/plugins/registry/list" \
"${BASE_URI}/plugins/hworld/_info" \
"${BASE_URI}/plugins/csls/_info" \
"${BASE_URI}/plugins/ainfo/_info" \
"${BASE_URI}/plugins/ainfo/500/description" \
	; do
	rest_request.py --uri "$uri"
done
#
#
curl -H "Accept: application/xml" "${BASE_URI}/plugins/badapple/prom/scafid/4?expand=true"
#
#
