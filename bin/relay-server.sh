#!/bin/bash

DIR=`dirname $0`
RELAY_DIR="$DIR/../relay-server"
SYSPROPS="-Dcom.sun.management.jmxremote -Djava.net.preferIPv4Stack=true -Djava.util.logging.config.file=$RELAY_DIR/bin/log.conf "

cd $RELAY_DIR && mvn $SYSPROPS exec:java -Dexec.mainClass=org.jgroups.relay_server.RelayServer -Dexec.args="-p 50151"
