#!/usr/bin/env sh

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Resolve links: $0 may be a link
PRG="$0"
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done
SAVED="`pwd`"
APP_HOME="`dirname "$PRG"`"
APP_HOME="`cd "$APP_HOME" >/dev/null && pwd -P`"
cd "$SAVED" >/dev/null

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

if [ ! -x "$JAVACMD" ] && ! command -v "$JAVACMD" >/dev/null 2>&1 ; then
    echo "ERROR: Java is required to run Gradle." >&2
    echo "Set JAVA_HOME or install a JDK." >&2
    exit 1
fi

exec "$JAVACMD" -Dorg.gradle.appname=gradlew -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
