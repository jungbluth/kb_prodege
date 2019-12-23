FROM kbase/sdkbase2:python
MAINTAINER Sean Jungbluth <sjungbluth@lbl.gov>
# -----------------------------------------
# In this section, you can install any system dependencies required
# to run your App.  For instance, you could place an apt-get update or
# install line here, a git checkout to download code, or run any other
# installation scripts.

WORKDIR /kb/module/bin

# To install all the dependencies
RUN apt-get update && apt-get install -y wget r-base gcc

# install perl and packages
RUN conda install -c bioconda perl-bioperl

RUN echo "install.packages(\"bigmemory\", repos=\"https://cran.rstudio.com\")" | R --no-save
RUN echo "install.packages(\"biganalytics\", repos=\"https://cran.rstudio.com\")" | R --no-save

RUN wget https://github.com/hyattpd/Prodigal/releases/download/v2.6.3/prodigal.linux && mv prodigal.linux /usr/local/bin/prodigal && chmod +x /usr/local/bin/prodigal

RUN wget ftp://ftp.ncbi.nlm.nih.gov/blast/executables/blast+/LATEST/*-x64-linux.tar.gz && tar xzf *-x64-linux.tar.gz && sh -c "find . -name 'blastn' -exec mv {} /usr/local/bin/ \;" && rm -rf ncbi-blast*
COPY ./ /kb/module

RUN mkdir -p /kb/module/work
RUN chmod -R a+rw /kb/module

WORKDIR /kb/module

ENV PERL5LIB=/data/prodege-2.3/lib:$PERL5LIB

RUN make all

ENTRYPOINT [ "./scripts/entrypoint.sh" ]

CMD [ ]
