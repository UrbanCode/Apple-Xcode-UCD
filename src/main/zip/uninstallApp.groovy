/*
* Licensed Materials - Property of IBM Corp.
* IBM UrbanCode Deploy
* (c) Copyright IBM Corporation 2011, 2014. All Rights Reserved.
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
def targetOS = props['targetOS']
def timeout = props['timeout']?: "300000"

Util.assertMacOS();

// The target is a device.
if (udid) {
    def result = Util.removeDeviceApp(bundleID, udid, null, timeout);
    if(result != 0) {
        println "Error: An error code of " + result + " occurred during " +
            "application removal from the device."
        System.exit(-1);
    }
} else {
    if(!targetOS?.trim()) {
        println "Error: No application removal target was specified.";
        System.exit(-1);
    }
    Util.removeSimulatorApp(bundleID, targetOS);
}

System.exit(0);