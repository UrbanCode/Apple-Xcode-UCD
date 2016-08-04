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

def simName = props['simName']
def simDeviceType = props['simDeviceType']
def targetOS = props['targetOS']
def xcrunPath = props['xcrunPath']

Util.assertMacOS();

def udid = Util.createSimulator(simName, simDeviceType.trim(), targetOS.trim(), xcrunPath);

apTool.setOutputProperty("deviceID", udid);
apTool.storeOutputProperties();

println "The simulator was created.";
System.exit(0);

