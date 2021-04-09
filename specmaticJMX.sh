if [ ! -e "./application/build/libs/specmatic.jar" ]; then
  ./gradlew clean build shadowJar -x test
fi
if [ ! -e "./jmx/jmx_prometheus_javaagent-0.15.0.jar" ]; then
  curl -L https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.15.0/jmx_prometheus_javaagent-0.15.0.jar --output ./jmx/jmx_prometheus_javaagent-0.15.0.jar
fi
java -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.port=5555 -javaagent:${PWD}/jmx/jmx_prometheus_javaagent-0.15.0.jar=8089:${PWD}/jmx/config.yml -jar ./application/build/libs/specmatic.jar $@
