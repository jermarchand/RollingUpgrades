FROM maven:3.5.2-jdk-8

RUN mkdir /app

COPY pom.xml /app/pom.xml
COPY bin /app/bin
COPY common /app/common
COPY jgroups-36 /app/jgroups-36
COPY jgroups-4 /app/jgroups-4
COPY jgroups-41 /app/jgroups-41
COPY relay-server /app/relay-server

RUN cd /app; mvn package

CMD ["java", \
     "-cp", \
     "/app/relay-server/target/relay-server-1.0.0.Final-jar-with-dependencies.jar", \
     "-Dcom.sun.management.jmxremote", \
     "-Djava.net.preferIPv4Stack=true", \
     "-Djava.util.logging.config.file=/app/bin/log.conf ", \
     "org.jgroups.relay_server.RelayServer", \
     "-port", "50151"]