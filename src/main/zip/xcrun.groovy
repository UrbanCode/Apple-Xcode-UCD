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

def xcrunPath = props['xcrunPath']
def additionalArgs = props['additionalArgs']
def timeout = props['timeout']

Util.assertMacOS();

System.exit(Util.xcrunCmd("Running the xcrun command.", additionalArgs, xcrunPath, timeout));