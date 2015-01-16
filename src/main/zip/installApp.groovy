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
def simName = props['simName']
def simDeviceType = props['simDeviceType']?: ""
def targetOS = props['targetOS']
boolean reinstall = Boolean.parseBoolean(props['reinstall'])
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

if (udid) {
    Util.isUDIDValid(xcrunPath, udid);
    if(Util.isSimUDID(udid)){
        Util.isAppValidForSimArch(udid, appFile);
        // If we are reinstalling, we don't need to check if the app is installed
        // since the application will be overwritten.
        if(!reinstall) {
            boolean isInstalled = Util.findSimulatorApp(bundleID, udid);
            if(isInstalled) {
                println "Error: The application ${app} is already installed."
                System.exit(-1);
            }
        }
        Util.installSimulatorApp(appFile, udid, xcrunPath);
    } else {
        Util.isAppValidForDeviceArch(appFile);
        // If we are reinstalling, we don't need to check if the app is installed
        // since the application will be overwritten.
        if(!reinstall) {
            boolean isInstalled = Util.findDeviceApp(bundleID, true, udid, null, timeout);
            if(isInstalled) {
                println "Error: The application ${app} is already installed."
                System.exit(-1);
            }
        }
        def result = Util.installDeviceApp(appFile, udid, null, timeout);
        if(result != 0) {
            println "Error: An error code of " + result + " occurred during " +
                "installation on the device."
            System.exit(-1);
        }
    }
} else {
    if((simName && !targetOS ) || (!simName && targetOS)) {
        println "Error: Both the Simulator Name and Target OS must be specified " + 
            "for application install.";
        System.exit(-1);
    }
    def simUDID = Util.findSimulatorUDID(simName, simDeviceType.trim(), targetOS.trim(), xcrunPath);
    Util.isAppValidForSimArch(simUDID, appFile);
    
    // If we are reinstalling, we don't need to check if the app is installed
    // since the application will be overwritten.
    if(!reinstall) {
        boolean isInstalled = Util.findSimulatorApp(bundleID, simUDID);
        if(isInstalled) {
            println "Error: The application ${app} is already installed."
            System.exit(-1);
        }
    }
    Util.installSimulatorApp(appFile, simUDID, xcrunPath);
}

println "The Install Application step completed.";
System.exit(0);