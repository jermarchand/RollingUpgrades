#!/bin/bash

DIR=`dirname $0`
RELAY_DIR="$DIR/../jgroups-4"
SYSPROPS="-Dcom.sun.management.jmxremote -Djava.net.preferIPv4Stack=true"


cd $RELAY_DIR && mvn $SYSPROPS exec:java -Dexec.mainClass=org.jgroups.demos.Chat \
 -Dexec.args="-props config.xml -name A"
