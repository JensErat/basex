FROM maven:3-jdk-7
MAINTAINER Jens Erat <email@jenserat.de>

COPY . /usr/src/basex/
RUN ls -la /usr/src
RUN ls -la /usr/src/basex
RUN ls -la /usr/src/basex-core
WORKDIR /usr/src/basex/basex-core
RUN mvn clean install -DskipTests

RUN adduser --system --home /srv --disabled-password --disabled-login --uid 1984 basex && \
    mkdir /srv/BaseXData /srv/BaseXRepo /srv/BaseXWeb && \
    chown basex /srv /srv/*
USER basex
# 1984/tcp: API
# 1985/tcp: Event
# 8984/tcp: HTTP
# 8985/tcp: HTTP stop
EXPOSE 1984 1985
VOLUME ["/srv/BaseXData"]

CMD ["/usr/src/basex/basex-core/etc/basexserver", "-d", "-z"]
