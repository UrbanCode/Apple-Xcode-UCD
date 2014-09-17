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
def xcrunPath = props['xcrunPath']
int startupRetries = Integer.parseInt(props['startupRetries']?:"10")

Util.assertMacOS();
// Only one simulator can run at a time.
if(Util.isSimulatorRunning()) {
    println "A simulator is already running.";
    System.exit(-1);
}

def simUDID = Util.findSimulatorUDID(simType.trim(), targetOS.trim(), xcrunPath);

Util.startSimulator(simUDID);

for (int i = 0; i < startupRetries; i++){
    if(Util.isSimulatorRunning(simUDID, xcrunPath)) {
        break;
    } else {
        if (i < (startupRetries-1)) {
            Thread.sleep(10000);
        } else {
            println "The simulator failed to start.";
            System.exit(-1);
        }
    }
}

println "The simulator has started.";
System.exit(0);

