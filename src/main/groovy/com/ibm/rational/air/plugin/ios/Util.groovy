/**
* Licensed Materials - Property of IBM
* 5748-XX8
* (C) Copyright IBM Corp. 2014 All Rights Reserved
* US Government Users Restricted Rights - Use, duplication or
* disclosure restricted by GSA ADP Schedule Contract with
* IBM Corp.
**/

package com.ibm.rational.air.plugin.ios;

import com.urbancode.air.CommandHelper;

/**
* A utility class for helping to run the iOS commands.
**/
public class Util {
    /**
    * Checks if the plug-in is running on Mac OS.
    **/
    public static void assertMacOS() {
        def osName = System.getProperty("os.name").toLowerCase(Locale.US)
        if(!osName.contains("mac os")) {
            println "The plug-in commands must be running on an agent installed on a " +
                "Mac machine.";
            System.exit(-1);
        }
    }
    
    /**
    * Checks if the simulator is running on the agent machine.
    * Returns true if the simulator is running, false otherwise.
    **/
    public static boolean isSimulatorRunning() {
        def ch = new CommandHelper(new File('.'));
        ch.ignoreExitValue(true);
        def args = ['pgrep', 'iPhone Simulator'];
        // The exit value is 0 for when a simulator is running, 1 otherwise.
        return !ch.runCommand("Checking simulator status.", args);
    }
}