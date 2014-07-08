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

def project = props['project']
def workspace = props['workspace']
def scheme = props['scheme']
def destination = props['destination']
def udid = props['udid']
def simType = props['simType']
def targetOS = props['targetOS']
def destinationTimeout = props['destinationTimeout']
def xcrunPath = props['xcrunPath']
def timeout = props['timeout']

Util.assertMacOS();

def args = ["xcodebuild", "test"];
if(project) {
    // The target is the project name, so find the project file.
    args << "-project";
    def xcodeproj;
    def curDir = new File(".");
    curDir.eachDir { it ->
        if(it.name.endsWith(".xcodeproj")) {
            xcodeproj = it.canonicalPath;
        } else {
            it.eachDir { subDir ->
                if(subDir.name.endsWith(".xcodeproj")) {
                    xcodeproj = subDir.canonicalPath;
                }
            }
        }
    }
    
    if(!xcodeproj) {
        println "Error: The xcodeproj file could not be found.";
        System.exit(-1);
    }
    
    args << xcodeproj;
} else if(workspace) {
    // The target is a workspace, so find the workspace file.
    args << "-workspace";
    def xcworkspace;
    def curDir = new File(".");
    curDir.eachDir { it ->
        if(it.name.endsWith(".xcworkspace")) {
             xcworkspace = it.canonicalPath;
        }
    }
    
    if(!xcworkspace) {
        println "Error: The xcworkspace file could not be found.";
        System.exit(-1);
    }
    
    args << xcworkspace;
}

if(scheme?.trim().length() == 0) {
    println "Error: A scheme name is required.";
    System.exit(-1);
}

args << "-scheme";
args << scheme.trim();

if(destination) {
    // Check if the argument is a file or line separated list
    def inFile = new File(destination);
    
    if(inFile.file) {
        destination = inFile.getText();
        System.out.println("Reading destination from: " + inFile.getCanonicalFile());
    }
    destination.eachLine { it ->
        if(it?.trim()) {
            args << "-destination";
            args << it.trim();
        }
    }
} else if (udid) {
    args << "-destination";
    args << "platform=iOS,id=" + udid.trim();
} else {
    // Check if only one of the simulator target properties are set.
    if(!simType) {
        println "Error: The Simulator Type must be specified when " +
            "specifying the simulator target to unit test against.";
        System.exit(-1);
    }
    args << "-destination";
    if(targetOS) {
        // Build up the String for the target.
        args << "platform=iOS Simulator,name=" + simType.trim() + 
            ",OS=" + targetOS.trim();
    } else {
        // Build up the String for the target.
        args << "platform=iOS Simulator,name=" + simType.trim() + ",OS=latest";
    }
}

if (destinationTimeout) {
    args << "-destination-timeout";
    args << destinationTimeout.trim();
}

System.exit(Util.unitTest(xcrunPath, "Running unit tests.", args, timeout));