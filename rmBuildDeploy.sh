#!/bin/bash
rm ~/programs/servers/eap6.2.1-forJONlib/jboss-eap-6.2/standalone/deployments/ispnweb.war
mvn -s /home/tsykora/programs/eclipseWorkspace/settings_mead_jdg_plus_local.xml clean package -Dmaven.test.skip=true
cp target/ispnweb.war ~/programs/servers/eap6.2.1-forJONlib/jboss-eap-6.2/standalone/deployments/
