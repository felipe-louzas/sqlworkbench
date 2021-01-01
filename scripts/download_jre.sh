#!/bin/sh

rm -f jre14.tar.gz

case "$(uname -s)" in
  Darwin)
    jre_os="mac"
    ;;
  Linux*)
    jre_os="linux"
    ;;
  *)
    echo "Unknown system: $(uname)"
    exit 1
    ;;
esac

curl --insecure -L "https://api.adoptopenjdk.net/v2/binary/releases/openjdk14?openjdk_impl=hotspot&os=${jre_os}&arch=x64&release=latest&type=jre" -o jre14.tar.gz

rm -Rf jre
mkdir jre

tar xf jre14.tar.gz --strip-components=1 --directory jre
