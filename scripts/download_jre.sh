#!/bin/sh

case "$(uname -s)" in
  Darwin)
    url="https://api.adoptopenjdk.net/v3/binary/latest/16/ga/mac/x64/jre/hotspot/normal/adoptopenjdk?project=jdk"
    ;;
  Linux*)
    url="https://www.sql-workbench.eu/jre/jre_linux64.tar.gz"
    ;;
  *)
    echo "Unknown system: $(uname)"
    exit 1
    ;;
esac

curl --insecure -L "${url}" -o jre16.tar.gz

rm -Rf jre
mkdir jre

tar xf jre16.tar.gz --strip-components=1 --directory jre
