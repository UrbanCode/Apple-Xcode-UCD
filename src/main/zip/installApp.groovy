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

def app = props['app']
def udid = props['udid']
def target = props['target']
boolean reinstall = Boolean.parseBoolean(props['reinstall'])
def xcode = props['xcode']?: "/Applications/Xcode.app"
def xcrunPath = props['xcrunPath']
def timeout = props['timeout']?: "300000"

Util.assertMacOS();

if(!app?.trim()) {
    println "Error: An application to install must be supplied."
    System.exit(-1);
}
def appFile = Util.handleApplication(app);
def bundleID = Util.getAppBundleID(appFile);
if(!bundleID) {
    println "An error occurred while trying to find the application bundle ID in " + 
        "order to determine if the application is installed.";
    System.exit(-1);
}

// The target is a device.
if (udid) {
    Util.isAppValidForDeviceArch(appFile);
    Util.isUDIDValid(xcrunPath, udid);
    boolean isInstalled = Util.findDeviceApp(bundleID, true, udid, null, timeout);
    if(isInstalled && !reinstall) {
        println "Error: The application ${app} is already installed."
        System.exit(-1);
    }
    def result = Util.installDeviceApp(app, udid, null, timeout);
    if(result != 0) {
        println "Error: An error code of " + result + " occurred during " +
            "installation on the device."
        System.exit(-1);
    }
} else {
    if(!target?.trim()) {
        println "Error: No application install target was specified.";
        System.exit(-1);
    }
    
    Util.isAppValidForSimArch(target, appFile);
    
    boolean isInstalled = Util.findSimulatorApp(bundleID, target, xcode);
    if(isInstalled && !reinstall) {
        println "Error: The application ${app} is already installed."
        System.exit(-1);
    }
    if(isInstalled && reinstall) {
        Util.removeSimulatorApp(bundleID, target, xcode);
    }
    Util.installSimulatorApp(app, target, xcode);
}

println "The Install Application step completed.";
System.exit(0);