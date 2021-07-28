let
    pkgs = import <nixpkgs> {};
    jdk8 = pkgs.adoptopenjdk-hotspot-bin-8;
in pkgs.mkShell {
    buildInputs = [ jdk8 pkgs.htop ];
    shellHook = ''
        SPECMATIC_JAR=`pwd`/application/build/libs/specmatic.jar
        alias specmatic="java -jar $SPECMATIC_JAR"

        echo Java package: ${jdk8.name}
        echo Specmatic version: `java -jar $SPECMATIC_JAR --version`
    '';
}
