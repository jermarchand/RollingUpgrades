#!/bin/bash

DIR=`dirname $0`
RELAY_DIR="$DIR/../relay-server"
SYSPROPS="-Dcom.sun.management.jmxremote -Djava.net.preferIPv4Stack=true"

cd $RELAY_DIR && mvn -o $SYSPROPS exec:java -Dexec.mainClass=org.jgroups.relay_server.Dump -Dexec.args="$*"
