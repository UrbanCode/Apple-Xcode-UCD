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
def targetOS = props['targetOS']
boolean reinstall = Boolean.parseBoolean(props['reinstall'])
def xcode = props['xcode']?: "/Applications/Xcode.app"
def timeout = props['timeout']?: "300000"

Util.assertMacOS();

if(!app?.trim()) {
    println "Error: An application to install must be supplied."
    System.exit(-1);
}
def appName;
if(app.contains(File.separator)) {
    appName = app.substring(app.lastIndexOf(File.separator) + 1);
} else {
    appName = app;
}

// The target is a device.
if (udid) {
    boolean isInstalled = Util.findDeviceApp(appName, false, udid, null, timeout);
    if(isInstalled && !reinstall) {
        println "Error: The application ${appName} is already installed."
        System.exit(-1);
    }
    def result = Util.installDeviceApp(app, udid, null, timeout);
    if(result != 0) {
        println "Error: An error code of " + result + " occurred during " +
            "installation on the device."
        System.exit(-1);
    }
} else {
    if(!targetOS?.trim()) {
        println "Error: No application install target was specified.";
        System.exit(-1);
    }
    
    boolean isInstalled = Util.findSimulatorApp(appName, false, targetOS);
    if(isInstalled && !reinstall) {
        println "Error: The application ${app} is already installed."
        System.exit(-1);
    }
    Util.installSimulatorApp(app, targetOS, xcode);
}

System.exit(0);