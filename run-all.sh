#!/bin/bash
# Runs the reproducer on 3.40.1, 4.0.0.Beta1 and current upstream main (built here).
B=~/quarkus-native/oidc-params
R=$B/repro
M2=$B/m2
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk
export PATH=$JAVA_HOME/bin:$PATH MAVEN_OPTS="-Xmx4g"
S=$B/status
echo "START $(date -u)" > $S
run() { # version profile tag
  cd $R
  ./mvnw -B -Dmaven.repo.local=$M2 clean test -Dquarkus.version=$1 $2 > $B/test-$3.log 2>&1
  echo "TEST $3 quarkus=$1 exit=$? $(date -u +%T) $(grep -E '^\[(INFO|ERROR|WARNING)\] Tests run:' $B/test-$3.log | grep -v ' -- in ' | tail -1)" >> $S
}
run 3.40.1 "" 3.40.1
run 4.0.0.Beta1 "-Pv4" 4.0.0.Beta1
cd $B && rm -rf quarkus-main && git clone -q --depth 1 https://github.com/quarkusio/quarkus.git quarkus-main
cd $B/quarkus-main && echo "MAIN $(git log -1 --format='%h %cd' --date=iso-strict)" >> $S
./mvnw -B -Dmaven.repo.local=$M2 -Dquickly -T 10 install > $B/build-main.log 2>&1
echo "BUILD main exit=$? $(date -u +%T) $(grep -E 'BUILD (SUCCESS|FAILURE)|Total time' $B/build-main.log | tr '\n' ' ')" >> $S
run 999-SNAPSHOT "-Pv4" main
echo "END $(date -u)" >> $S
