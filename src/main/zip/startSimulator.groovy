/*
* Licensed Materials - Property of IBM Corp.
* IBM UrbanCode Deploy
* (c) Copyright IBM Corporation 2011, 2014. All Rights Reserved.
*
* U.S. Government Users Restricted Rights - Use, duplication or disclosure restricted by
* GSA ADP Schedule Contract with IBM Corp.
*/

/**
* This script depends on configSimulator.scpt to modify the simulator
* configuration before start up.
**/

import com.urbancode.air.AirPluginTool;
import com.ibm.rational.air.plugin.ios.Util;

def apTool = new AirPluginTool(this.args[0], this.args[1]);
final def props = apTool.getStepProperties();

def simType = props['simType']?: ""
def targetOS = props['targetOS']?: ""
def xcode = props['xcode']?: "/Applications/Xcode.app"

Util.assertMacOS();
// Only one simulator can run at a time.
if(Util.isSimulatorRunning()) {
    println "A simulator is already running.";
    System.exit(-1);
}

Util.startSimulator(simType, targetOS, xcode);

if(!Util.isSimulatorRunning()) {
    println "The simulator failed to start.";
    System.exit(-1);
}

println "The simulator has started.";
System.exit(0);