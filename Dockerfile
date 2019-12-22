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

RUN echo "install.packages(\"bigmemory\", repos=\"https://cran.rstudio.com\")" | R --no-save
RUN echo "install.packages(\"biganalytics\", repos=\"https://cran.rstudio.com\")" | R --no-save

COPY ./ /kb/module

RUN mkdir -p /kb/module/work
RUN chmod -R a+rw /kb/module

WORKDIR /kb/module

RUN make all

ENTRYPOINT [ "./scripts/entrypoint.sh" ]

CMD [ ]
