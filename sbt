#!/bin/bash
if [[ -n "$JAVA_HOME" ]]; then
  JAVA_CMD=$JAVA_HOME/bin/java
else
  JAVA_CMD=$(which java)
fi

SBT_OPTS="-XX:MaxPermSize=512m -Xmx1024m"
REARVIEW_ARGS="-Dsbt.ivy.home=.ivy2 -Dfile.encoding=UTF-8 -Djava.security.manager -Djava.security.policy=security.policy -Dsun.net.inetaddr.ttl=5 -Dsun.net.inetaddr.negative.ttl=0 -Djava.net.preferIPv4Stack=true"

$JAVA_CMD $REARVIEW_ARGS $JAVA_ARGS $SBT_OPTS -jar `dirname $0`/libs/sbt-launch.jar "$@"
