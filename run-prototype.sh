#!/bin/bash
# After run-all.sh: apply the option C prototype to the main checkout, rebuild oidc-client, rerun the reproducer.
B=~/quarkus-native/oidc-params
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH MAVEN_OPTS="-Xmx4g"
S=$B/status
until grep -q '^END' $S; do sleep 15; done
cd $B/quarkus-main
cp $B/repro/prototype/TokensHelper.java extensions/oidc-client/runtime/src/main/java/io/quarkus/oidc/client/runtime/TokensHelper.java
git diff --stat >> $S
./mvnw -B -Dmaven.repo.local=$B/m2 install -f extensions/oidc-client > $B/build-prototype.log 2>&1
echo "BUILD prototype exit=$? $(date -u +%T) $(grep -E '^\[(INFO|ERROR)\] Tests run:' $B/build-prototype.log | grep -v ' -- in ' | tail -1)" >> $S
cd $B/repro && ./mvnw -B -Dmaven.repo.local=$B/m2 clean test -Dquarkus.version=999-SNAPSHOT -Pv4 > $B/test-prototype.log 2>&1
echo "TEST prototype exit=$? $(date -u +%T) $(grep -E '^\[(INFO|ERROR|WARNING)\] Tests run:' $B/test-prototype.log | grep -v ' -- in ' | tail -1)" >> $S
echo "PROTO-END $(date -u)" >> $S
