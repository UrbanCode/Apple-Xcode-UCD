/*
* Licensed Materials - Property of IBM Corp.
* IBM UrbanCode Deploy
* (c) Copyright IBM Corporation 2011, 2015. All Rights Reserved.
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

def udid = props['udid']
def simName = props['simName']
def simDeviceType = props['simDeviceType']?: ""
def targetOS = props['targetOS']
def xcode = props['xcode']?: "/Applications/Xcode.app"
def xcrunPath = props['xcrunPath']
int startupRetries = Integer.parseInt(props['startupRetries']?:"10")

Util.assertMacOS();
// Only one simulator can run at a time.
if(Util.isSimulatorRunning()) {
    println "A simulator is already running.";
    System.exit(-1);
}

if (udid) {
    Util.isUDIDValid(xcrunPath, udid);
    if(!Util.isSimUDID(udid)){
        println "Error: The simulator unique device identifier " +
            "(UDID) " + udid + " to start was not found."
        System.exit(-1);
    }
} else {
    if(!(simName && targetOS)) {
        println "Error: Both the Simulator Name and Target OS must be specified " +
            "to start the application.";
        println "Explanation: This error can occur if neither Simulator Name nor Target OS are defined.";
        println "User response: Verify that the Simulator Name and Target OS, or " +
            "Device Identifier are defined for the Start Simulator step.";
        System.exit(-1);
    }
    udid = Util.findSimulatorUDID(simName, simDeviceType.trim(), targetOS.trim(), xcrunPath);
}

def xcodeApp = Util.verifyXcodePath(xcode);

def simulatorApp = new String(File.separator + "Contents" + File.separator + "Developer" +
    File.separator + "Applications" + File.separator +
    "iOS Simulator.app");

def simulatorAppPath;
try {
    simulatorAppPath = new File(xcodeApp, simulatorApp);
} catch (Exception e) {
    println "An error occurred during an attempt to access the iOS simulator " +
        "app: " + e.getMessage();
    System.exit(-1);
}
if(!simulatorAppPath.directory) {
    println "Error: The path to the iOS simulator app is incorrect: " +
        simulatorAppPath.canonicalPath;
    System.exit(-1);
}

Util.startSimulator(udid, simulatorAppPath);

for (int i = 0; i < startupRetries; i++){
    if(Util.isSimulatorRunning(udid, xcrunPath)) {
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

