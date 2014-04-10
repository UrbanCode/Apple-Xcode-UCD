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
import com.urbancode.air.CommandHelper;
import com.ibm.rational.air.plugin.ios.Util;

def apTool = new AirPluginTool(this.args[0], this.args[1]);
final def props = apTool.getStepProperties();

def deviceType = props['deviceType']?: ""
def targetOS = props['targetOS']?: ""
def xcode = props['xcode']?: "/Applications/Xcode.app"

Util.assertMacOS();
// Only one simulator can run at a time.
if(Util.isSimulatorRunning()) {
    println "A simulator is already running.";
    System.exit(-1);
}

// Update the device type and target SDK OS before launching the simulator.
if(deviceType?.trim() && targetOS?.trim()) {
    def xcodeApp = Util.verifyXcodePath(xcode);
    
    try {
        def pathToXcode = new String(File.separator + "Contents" + File.separator + 
            "Developer" + File.separator + "Platforms" + File.separator + 
            "iPhoneSimulator.platform" + File.separator + "Developer" + File.separator + 
            "SDKs" + File.separator + "iPhoneSimulator" + targetOS + ".sdk");
        xcodeApp = new File(xcodeApp, pathToXcode);
    } catch (Exception e) {
        println "An error occurred during an attempt to access the simulator " + 
            "target OS: " + e.getMessage();
        System.exit(-1);
    }
    if(!xcodeApp.directory) {
        println "Error: The path to the simulator target OS is incorrect: " + 
            xcodeApp.canonicalPath;
        println "Possibly the simulator SDK is not installed.";
        System.exit(-1);
    }
    
    // The script file is located in the plug-in.
    final def PLUGIN_HOME = new File(System.getenv().get("PLUGIN_HOME"));
    def scriptLoc = PLUGIN_HOME.canonicalPath + File.separator + 'configSimulator.scpt';
    
    // Run the script to change the simulator configuration.
    def simCH = new CommandHelper(new File('.'));
    def simArgs = ['osascript', scriptLoc, deviceType, xcodeApp.canonicalPath];
    
    simCH.runCommand("Updating the simulator configuration.", simArgs);
    
    def plistLoc = System.getenv().get("HOME") + File.separator + "Library" +
        File.separator + "Preferences" + File.separator + "com.apple.iphonesimulator";
    simArgs = ['defaults', 'read', plistLoc];
    simCH.runCommand("Refreshing the simulator configuration.", simArgs);
     
    println "The simulator will launch an ${deviceType} using SDK ${targetOS}.";
} else {
    if((deviceType && !targetOS ) || (!deviceType && targetOS)) {
        println "Error: Both the Device Type and Target OS must be specified when changing " +
            "the simulator configuration.";
        System.exit(-1);
    }
} 

// Start the simulator.
def ch = new CommandHelper(new File('.'));
def args = ['osascript', '-e', 'tell application \"iPhone Simulator\" to launch'];

ch.runCommand("Starting the simulator.", args);

if(!Util.isSimulatorRunning()) {
    println "The simulator failed to start.";
    System.exit(-1);
}

println "The simulator has started.";
System.exit(0);