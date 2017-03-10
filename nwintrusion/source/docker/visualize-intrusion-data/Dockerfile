from lightningviz/lightning

RUN mkdir -p /usr/src/vis/jupyter

# Install jupyter

RUN apt-get -q update && \
apt-get -q -y install python3 && \
apt-get -q -y install python3-pip && \
pip3 install jupyter

# For add-apt-repository
RUN apt-get -q -y install software-properties-common

RUN wget -qO - http://packages.confluent.io/deb/3.0/archive.key | apt-key add - && \
add-apt-repository "deb [arch=amd64] http://packages.confluent.io/deb/3.0 stable main" && \
apt-get -q update && \
apt-get -q -y install librdkafka-dev && \
pip3 install confluent-kafka

RUN mkdir -p /root/.jupyter

RUN touch /root/.jupyter/jupyter_notebook_config.py

RUN echo "c.NotebookApp.password = u'sha1:691a5426ef6e:27587f9d97e2b5c3d0ddb384203532eeb3997de9'" >> /root/.jupyter/jupyter_notebook_config.py

RUN pip3 install numpy

RUN pip3 install lightning-python

ADD ./image-files/run.sh /usr/src/vis/

ADD ./image-files/vis.ipynb /usr/src/vis/jupyter

ENTRYPOINT ["/usr/src/vis/run.sh"]
