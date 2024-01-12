#!/usr/bin/env bash
# Start SQL Workbench/J in console mode

function readlink() {
  case `uname -s` in
    Linux*)
      command readlink -e "$@"
      ;;
    *)
      command readlink "$@"
      ;;
  esac
}

SCRIPT_PATH=$(dirname -- "$(readlink "${BASH_SOURCE[0]}")")
JAVACMD="java"

if [ -x "$SCRIPT_PATH/jre/bin/java" ]
then
  JAVACMD="$SCRIPT_PATH/jre/bin/java"
elif [ -x "$SCRIPT_PATH/jre/Contents/Home/bin/java" ]
then
  # MacOS
  JAVACMD="$SCRIPT_PATH/jre/Contents/Home/bin/java"
elif [ -x "$WORKBENCH_JDK/bin/java" ] && [ -n "${WORKBENCH_JDK}" ]
then
  JAVACMD="$WORKBENCH_JDK/bin/java"
elif [ -x "$JAVA_HOME/jre/bin/java" ] && [ -n "${JAVA_HOME}" ]
then
  JAVACMD="$JAVA_HOME/jre/bin/java"
elif [ -x "$JAVA_HOME/bin/java" ] && [ -n "${JAVA_HOME}" ]
then
  JAVACMD="$JAVA_HOME/bin/java"
# IBMi default locations
# https://www.ibm.com/support/pages/how-determine-what-java-development-kits-jdks-are-installed-and-use-them-your-environment
elif [ -x "/QOpenSys/QIBM/ProdData/JavaVM/jdk17/64bit/bin/java" ]
then
  JAVACMD="/QOpenSys/QIBM/ProdData/JavaVM/jdk17/64bit/bin/java"
elif [ -x "/QOpenSys/QIBM/ProdData/JavaVM/jdk11/64bit/bin/java" ]
then
  JAVACMD="/QOpenSys/QIBM/ProdData/JavaVM/jdk11/64bit/bin/java"
fi

cp="$SCRIPT_PATH/sqlworkbench.jar"
cp="$cp:$SCRIPT_PATH/ext/*"


"$JAVACMD" -Djava.awt.headless=true \
           --add-opens java.base/java.lang=ALL-UNNAMED \
           -Dvisualvm.display.name=SQLWorkbench/J \
           -cp "$cp" workbench.console.SQLConsole "$@"
