#!/bin/bash
rm ~/programs/servers/eap6.0.0-forJONlib/jboss-eap-6.0/standalone/deployments/ispnweb.war
mvn -o clean package -Dmaven.test.skip=true
cp target/ispnweb.war ~/programs/servers/eap6.0.0-forJONlib/jboss-eap-6.0/standalone/deployments/
