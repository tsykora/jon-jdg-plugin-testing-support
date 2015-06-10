#!/bin/bash
rm ~/servers/eap/jboss-eap-6.4-forJON/standalone/deployments/ispnweb.war 
mvn clean package -Dmaven.test.skip=true 
cp target/ispnweb.war ~/servers/eap/jboss-eap-6.4-forJON/standalone/deployments/
