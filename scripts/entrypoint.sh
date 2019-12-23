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
  wget http://1ofdmq2n8tc36m6i46scovo2e-wpengine.netdna-ssl.com/wp-content/uploads/2018/05/prodege-2.3.tar_.gz
  tar -xvzf prodege-2.3.tar_.gz

  # streamlining assignment of binaries
  sed -i 's/\$PCmd/prodigal/' /data/prodege-2.3/bin/prodege.sh
  sed -i 's/\$blastCmd/blastn/' /data/prodege-2.3/bin/prodege.sh

  #not a necessary perl redirect
  sed -i 's/use lib /#use lib /' /data/prodege-2.3/bin/prodege_compute_kmer_counts.pl

  # commenting this line out because R packages installed in Dockerfile
  sed -i 's/sh \$CURR_DIR.bin.02.getRpackages.sh/#sh \$CURR_DIR\/bin\/02.getRpackages.sh/' /data/prodege-2.3/prodege_install.sh

  # this next command ignores the installation checks, which are written too specifically
  head -99 /data/prodege-2.3/prodege_install.sh > ./tmpfile && mv ./tmpfile /data/prodege-2.3/prodege_install.sh && chmod +x /data/prodege-2.3/prodege_install.sh

  # R packages can be called without any extra flags
  sed -i 's/,lib.loc=bin//' /data/prodege-2.3/bin/prodege_classify_cleanandcontam.R
  sed -i 's/,lib.loc=bin//' /data/prodege-2.3/bin/prodege_classify_nobintarget.R
  sed -i 's/,lib.loc=bin//' /data/prodege-2.3/bin/prodege_classify_noclean.R
  sed -i 's/,lib.loc=bin//' /data/prodege-2.3/bin/prodege_classify_nocontam.R

  # Bugfix for R function where two tables were merged based on non-matching columns. Needed to rename a column (variable 'n')
  sed -i 's/^s=.*/s=rbind(as.matrix(cbind(sc,"clean")),as.matrix(cbind(sd,"contam"))); n=data.frame(V1=paste(data.frame(do.call("rbind", strsplit(as.character(n$V1),"_",fixed=TRUE)))[,1],data.frame(do.call("rbind", strsplit(as.character(n$V1),"_",fixed=TRUE)))[,2],sep="_"))/' /data/prodege-2.3/bin/prodege_classify_cleanandcontam.R

  # Copy ProDeGe specific perl module to library
  mv /data/prodege-2.3/lib/LexWords.pm /kb/deployment/lib/LexWords.pm

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
