/*
* Licensed Materials - Property of IBM Corp.
* IBM UrbanCode Deploy
* (c) Copyright IBM Corporation 2011, 2015. All Rights Reserved.
*
* U.S. Government Users Restricted Rights - Use, duplication or disclosure restricted by
* GSA ADP Schedule Contract with IBM Corp.
*/

import com.urbancode.air.AirPluginTool;
import com.ibm.rational.air.plugin.ios.Util;

def apTool = new AirPluginTool(this.args[0], this.args[1]);
final def props = apTool.getStepProperties();

def bundleID = props['bundleID']
def udid = props['udid']
def simName = props['simName']
def simDeviceType = props['simDeviceType']?: ""
def targetOS = props['targetOS']
def xcrunPath = props['xcrunPath']
def timeout = props['timeout']?: "300000"

Util.assertMacOS();

if (udid) {
    Util.isUDIDValid(xcrunPath, udid);
    if(Util.isSimUDID(udid)){
        Util.removeSimulatorApp(bundleID, udid, xcrunPath);
    } else {
        def result = Util.removeDeviceApp(bundleID, udid, null, timeout);
        if(result != 0) {
            println "Error: An error code of " + result + " occurred during " +
                "application removal from the device."
            System.exit(-1);
        }
    }
} else {
    if(!(simName && targetOS)) {
        println "Error: Both the Simulator Name and Target OS must be specified " +
            "for application removal.";
        println "Explanation: This error can occur if neither Simulator Name nor Target OS are defined.";
        println "User response: Verify the Simulator Name and Target OS, or " +
            "Device Identifier are defined for the Uninstall Application step.";
        System.exit(-1);
    }
    def simUDID = Util.findSimulatorUDID(simName, simDeviceType.trim(), targetOS.trim(), xcrunPath);
    Util.removeSimulatorApp(bundleID, simUDID, xcrunPath);
}

println "The Remove Application step completed.";
System.exit(0);