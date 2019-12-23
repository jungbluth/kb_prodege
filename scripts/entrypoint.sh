#!/bin/bash

. /kb/deployment/user-env.sh

python ./scripts/prepare_deploy_cfg.py ./deploy.cfg ./work/config.properties

if [ -f ./work/token ] ; then
  export KB_AUTH_TOKEN=$(<./work/token)
fi

if [ $# -eq 0 ] ; then
  sh ./scripts/start_server.sh
elif [ "${1}" = "test" ] ; then
  echo "Run Tests"
  make test
elif [ "${1}" = "async" ] ; then
  sh ./scripts/run_async.sh
elif [ "${1}" = "init" ] ; then
  echo "Initialize module"
  echo "Setting up ProDeGe databases"
  mkdir -p /data
  cd /data
  mv /kb/module/lib/kb_prodege/bin/prodege-2.3 /data/prodege-2.3

  # Copy ProDeGe specific perl module to library, need only one of these
  mv /data/prodege-2.3/lib/LexWords.pm /kb/deployment/lib/LexWords.pm
  mv /kb/module/lib/kb_prodege/bin/prodege-2.3/lib/LexWords.pm /kb/deployment/lib/LexWords.pm

  cd /data/prodege-2.3
  /data/prodege-2.3/prodege_install.sh -i /data/prodege-2.3
  #if [ -f "/data/METABOLIC/pepunit.lib" ] ; then # need to make sure this file is present, wasn't working
  touch /data/__READY__
  #else
  #  echo "Init failed"
  #fi
elif [ "${1}" = "bash" ] ; then
  bash
elif [ "${1}" = "report" ] ; then
  export KB_SDK_COMPILE_REPORT_FILE=./work/compile_report.json
  make compile
else
  echo Unknown
fi
