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
    * Util Standard Variables' Names:
    * targetOS: The OS of the simulator without architecture option (e.g: 7.0).
    * simName: The simulator name (e.g: 'iPhone 5s', 'myIphone').
    * simDeviceType: The simulator device type can be found in
    *   ~/Library/Developer/CoreSimulator/Devices/<device ID>/device.plist.
    *  (e.g: com.apple.CoreSimulator.SimDeviceType.iPhone-5s).
    ** /

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
        def args = ['pgrep', 'iOS Simulator'];
        // The exit value is 0 for when a simulator is running, 1 otherwise.
        return !ch.runCommand("Checking simulator status.", args);
    }
    
    /**
    * Checks if a specific simulator is running on the agent machine.
    * udid: The unique identifier of the simulator.
    * xcrunPath: An optional path to the xcrun tool.
    * Returns true if the simulator is running, false otherwise.
    **/
    public static boolean isSimulatorRunning(def udid, def xcrunPath) {
        def isStarted = false;
        def args = ['simctl', 'list', 'devices'];
        def ch = new CommandHelper(new File('.'));
        ch.ignoreExitValue(true);
        int result = runXcrunCmd("Checking simulator status.", args, xcrunPath,
            null) { builder ->
            def log = builder.toString();
            // Output the log to the console.
            println log;
            // Locate the simulator entry by udid.
            log = log.find(/.*\(${udid}\).*/);
            if(log == null) {
                println "Error: The simulator with UDID " + udid + " could not be found.";
                println "Explanation: This error can occur if the simulator configuration" +
                    "does not exist.";
                println "User response: Verify the simulator name and target OS are " +
                    "correct for the UDID, and the simulator exists.";
                System.exit(-1);
            }
            // Pull out the status from the entry.
            isStarted = log.contains("Booted");
        }
        if(result != 0) {
            println "Error: Running the Xcrun command failed with error code: " + result;
            System.exit(-1);
        }
        return isStarted;
    }
    
    /**
    * Checks if the provided unique device identifier, UDID, is valid (i.e. available
    * on the system for use).
    * xcrunPath: An optional path to the xcrun tool.
    * udid: The unique device identifier.
    **/
    public static void isUDIDValid(def xcrunPath, def udid) {
        def args = ['instruments', '-s', 'devices'];
        int result = runXcrunCmd("Verifying device IDs.", args, xcrunPath,
            null) { builder ->
            def log = builder.toString();
            // Output the log to the console.
            println log;
            // Determine if the UDID is in the list. The identifier will be surrounded
            // by square brackets, so we add those to make sure the entire String is found.
            log = log.find(/.*\[${udid}\]/);
            if(log == null) {
                println "Error: The " + udid + " device identifier could not be found.";
                println "Explanation: This error can occur if the device identifier is " +
                    "incorrect or the device is not attached.";
                println "User response: Verify the device identifier is correct and " +
                    "the device is attached to the agent computer.";
                System.exit(-1);
            }
        }
        if(result != 0) {
            println "Error: Running the Xcrun command failed with error code: " + result;
            System.exit(-1);
        }
    }
        
    /**
    * Checks if the provided application is valid for the device platform.
    * pathToApp: The local path to the .app directory to validate.
    **/
    public static void isAppValidForDeviceArch(def pathToApp) {
        def appDir;
        try {
            appDir = new File(pathToApp);
        } catch (Exception e) {
            println "The application file could not be accessed: " +
                e.getMessage();
            System.exit(-1);
        }
        if(!appDir?.isDirectory()) {
            println "The path to the application was not found.";
            System.exit(-1);
        }
        
        def deviceSupported = false;
        def ch = new CommandHelper(new File('.'));
        ch.ignoreExitValue(true);
        appDir.eachFile { infoFile ->
            if (infoFile.name == "Info.plist") {
                def infoPath = appDir.canonicalPath + File.separator + 'info';
                def args = ['defaults', 'read', infoPath, 'CFBundleSupportedPlatforms'];
                ch.runCommand("Check supported platforms.", args) { proc ->
                    InputStream inStream =  proc.getInputStream();
                    inStream.eachLine { line ->
                        if (!deviceSupported && line.trim() == "iPhoneOS"){
                            deviceSupported = true;
                        }
                    }
                }
            }
        }
        
        if(!deviceSupported) {
            println "Error: The device architecture does not support the application.";
            println "Explanation: This error can occur if the application is not " +
                "built for the iphoneos configuration.";
            println "User response: Verify that the application is built for the correct " +
                "target, for example, iphoneos.";
            System.exit(-1);
        }
    }
    
    /**
    * Checks if the provided application is valid for the target configuration.
    * udid: The unique device identifier of the simulator to check.
    * pathToApp: The local path to the .app directory to validate.
    **/
    public static void isAppValidForSimArch(def udid, def pathToApp) {
        def appDir;
        try {
            appDir = new File(pathToApp);
        } catch (Exception e) {
            println "The application file could not be accessed: " +
                e.getMessage();
            System.exit(-1);
        }
        if(!appDir?.isDirectory()) {
            println "The path to the application was not found.";
            System.exit(-1);
        }
        
        def bundleName = null;
        def ch = new CommandHelper(new File('.'));
        ch.ignoreExitValue(true);
        appDir.eachFile { infoFile ->
            if (infoFile.name == "Info.plist") {
                def infoPath = appDir.canonicalPath + File.separator + 'info';
                def args = ['defaults', 'read', infoPath, 'CFBundleName'];
                ch.runCommand("Check bundle name.", args) { proc ->
                    InputStream inStream =  proc.getInputStream();
                    List lines = inStream.readLines();
                    if(lines.size == 0) {
                        println "The bundle name was not found.";
                        System.exit(-1);
                    }
                    //The first line is the bundle name (ignore the new line).
                    bundleName = lines.get(0);
                }
            }
        }
        
        // Run the file command to check the architecture of the app.
        def arch = null;
        def args = ['file', appDir.canonicalPath + File.separator + bundleName];
        ch.runCommand("Check app architecture.", args) { proc ->
            InputStream inStream =  proc.getInputStream();
            List lines = inStream.readLines();
            if(lines.size == 0) {
                println "The application architecture could not be found.";
                System.exit(-1);
            }
            //The first line is the architecture (ignore the new line).
            arch = lines.get(0);
        }
        
        // Determine the simulator version.
        def deviceInfo = getSimulatorPath(udid);
        try {
            deviceInfo = new File(deviceInfo, "device.plist");
        } catch (Exception e) {
            println "An error occurred during an attempt to access the simulator device " +
                "plist file: " + e.getMessage();
            System.exit(-1);
        }
        
        if (deviceInfo == null || !deviceInfo.file){
            println "Error: Not able to find the simulator device plist file: " + 
                    deviceInfo.canonicalPath;
            System.exit(-1);
        }
        def simDeviceType;
        args = ['defaults', 'read', deviceInfo, 'deviceType'];
        ch.runCommand("Check simulator device type.", args) { proc ->
            InputStream inStream =  proc.getInputStream();
            List lines = inStream.readLines();
            if(lines.size == 0) {
                println "The simulator device type was not found.";
                System.exit(-1);
            }
            //The first line is the device type (ignore the new line).
            simDeviceType = lines.get(0);
        }
        
        println "The simulator device type to check: " + simDeviceType;
        println "Output of the file command (Tip: The app architecture is typically " +
            "at the end and of the form i386 or x86_64): " + arch;
        /*
        * 64-bit targets should be able to run 32-bit and 64-bit apps
        * Check if the device is in the list of 32-bit platforms.
        * Otherwise, we can run the application on the simulator.
        */
        if((simDeviceType.contains("com.apple.CoreSimulator.SimDeviceType.iPhone-4s") ||
            simDeviceType.contains("com.apple.CoreSimulator.SimDeviceType.iPhone-5") ||
            simDeviceType.contains("com.apple.CoreSimulator.SimDeviceType.iPad-2") ||
            simDeviceType.contains("com.apple.CoreSimulator.SimDeviceType.iPad-Retina")) &&
            arch.contains("x86_64")) {
        
            println "Error: The target simulator does not support the application " +
                "architecture.";
            println "Explanation: This error can occur if the application is not " +
                "built for the architecture that is in use.";
            println "User response: Verify that the application is built for the specified " +
                "architecture (for example, 32-bit).";
            System.exit(-1);
        }
    }

    /**
     * Starts the simulator.
     * udid: The unique device identifier of the simulator to check.
     **/
     public static void startSimulator(def udid) {
         // Update the device type and target SDK OS before launching the simulator.
         if(udid?.trim()) {
             def simCH = new CommandHelper(new File('.'));
             def simArgs = ['defaults', 'write', 'com.apple.iphonesimulator', 'CurrentDeviceUDID', udid];
             simCH.runCommand("Refreshing the simulator configuration.", simArgs);              
             println "The simulator with UDID ${udid} will be launched.";
         } else {
             println "Error: The simulator UDID must be specified when changing " +
                 "the simulator configuration.";
             System.exit(-1);
         }
         
         // Start the simulator.
         def ch = new CommandHelper(new File('.'));
         def args = ['osascript', '-e', 'tell application \"iOS Simulator\" to launch'];
         
         ch.runCommand("Starting the simulator.", args);
     }
    
    /**
    * Stops the running simulator.
    **/
    public static void stopSimulator() {
        def ch = new CommandHelper(new File('.'));
        def args = ['osascript', '-e', 'tell application \"iOS Simulator\" to quit'];

        ch.runCommand("Stopping the simulator.", args);
    }
    
    /**
     * Create a simulator.
     * simName: The name of the simulator configuration to create.
     * simDeviceType: The simulator device type (e.g: iPhone 5s).
     * targetOS: The OS of the simulator without architecture option (e.g: 7.0).
     * xcrunPath: An optional path to the xcrun tool.
     **/
    public static void createSimulator (def simName, def simDeviceType, def targetOS, def xcrunPath) {
        targetOS = targetOS.replaceFirst(/\./, '-');
        targetOS = targetOS.split("\\.")[0];
        targetOS = "com.apple.CoreSimulator.SimRuntime.iOS-"+targetOS;
        
        def args = ['simctl', 'create', simName, simDeviceType, targetOS];
        int result = runXcrunCmd("Creating the simulator.", args, xcrunPath,
            null) { builder ->
            def log = builder.toString();
            // Output the log to the console.
            println log;
        }
        if(result != 0) {
            println "Error: Running the Xcrun command failed with error code: " + result;
            System.exit(-1);
        }
    }

    /**
     * Delete a simulator.
     * udid: The unique device identifier of the simulator to delete.
     * xcrunPath: An optional path to the xcrun tool.
     **/
    public static void deleteSimulator (def udid, def xcrunPath) {
        def args = ['simctl', 'delete', udid];
        int result = runXcrunCmd("Deleting the simulator.", args, xcrunPath,
            null) { builder ->
            def log = builder.toString();
            // Output the log to the console.
            println log;
        }
        if(result != 0) {
            println "Error: Running the Xcrun command failed with error code: " + result;
            System.exit(-1);
        }
    }
    
    /**
    * Takes a .app or .ipa file and returns the application (.app) file for use
    * by other methods. If the file provided is a .ipa, the .app is copied to 
    * the working directory.
    * appPath: The path to the .app or .ipa file.
    * Returns the path to the .app file.
    **/
    public static def handleApplication(def appPath) {
        def app;
        try {
            app = new File(appPath);
        } catch (Exception e) {
            println "An error occurred during an attempt to access the application: " +
                e.getMessage();
            System.exit(-1);
        }
        if(!app.exists()) {
            println "Error: The path to the application is incorrect: " + 
                app.canonicalPath;
            System.exit(-1);
        }
        
        // Extract the .app file, use the system commands to retain permissions
        if(app.name.endsWith(".ipa")) {
            def workingDir = File.createTempFile("ucdIOS", "");
            workingDir.delete();
            workingDir.mkdir();
            def ch = new CommandHelper(new File('.'));
            def args = ['unzip', '-q', app.canonicalPath, '-d', workingDir.canonicalPath];

            ch.runCommand("Extracting the application.", args);
            def payloadDir = new File(workingDir, "Payload");
            payloadDir.eachFile { it ->
                if(it.name.endsWith(".app")) {
                    def curDir = new File('.');
                    // Note: This will overwrite any existing .app in the working directory.
                    try {
                        def curDirFile = new File(curDir, it.name);
                        if(curDirFile.exists()) {
                            println "Removing " + curDirFile.canonicalPath;
                            curDirFile.deleteDir();
                        }
                    } catch (Exception e) {
                        println "An error occurred during an attempt to remove the existing " +
                            "application directory in the working directory: " + e.getMessage();
                        System.exit(-1);
                    }
                    args = ['mv', '-f', it.canonicalPath, curDir.canonicalPath];
                    ch = new CommandHelper(curDir);
                    ch.runCommand("Moving the following file to the working directory: " +
                        it.canonicalPath, args);
                    app = new File(curDir, it.name);
                }
            }
            workingDir.deleteDir();
        }
        
        if(!app.name.endsWith(".app")) {
            println "Error: The ${app.name} application file name must exist and include " +
                "either the ipa or app extension."
            System.exit(1)
        }
        
        return app.canonicalPath;
    }
    
    /**
    * Runs the instruments command for UI testing with the provided 
    * options. Uses the xcrun command to find the tool. Use 
    * xcode-select to set the system default for developer tools.
    * See
    * https://developer.apple.com/library/mac/documentation/Darwin/Reference/ManPages/man1/instruments.1.html
    * https://developer.apple.com/library/mac/documentation/Darwin/Reference/Manpages/man1/xcrun.1.html.
    * xcrunPath: An optional path to the xcrun tool.
    * outputDir: An optional path to the output directory. This is the
    *   location the tool will be run in to ensure the output trace file name
    *   is unique and for import of the result by the user.
    * message: An optional message to output when the command is run.
    * arguments: The arguments to run the command with.
    * timeout: A period after which the xcrun command is stopped in
    *    milliseconds.
    * Returns the exit code of the command.
    **/
    public static int uiTest(def xcrunPath, def outputDir, def message, 
        def arguments, def timeout) {

        int result = runXcrunCmd(message, arguments, xcrunPath,
            timeout) { builder ->
            def log = builder.toString();
            // Output the log to the console.
            println log;
            // Find out the output location
            log = log.find("Output : .*\\.trace");
            if(log == null) {
                println "Error: The output location for the test could not be determined.";
                System.exit(-1);
            }
            def logLocation = log.split(':');
            if(logLocation.length != 2) {
                println "Error: An error occurred trying to find the trace output " +
                    "file location.";
                System.exit(-1);
            }
            log = logLocation[1].trim();
            def ant = new AntBuilder();
            def newFilename = System.currentTimeMillis() + ".trace";
            println "Renaming the output file " +
                log.substring(log.lastIndexOf(File.separator) + 1) + " to ${newFilename}.";
            // First we rename to the timestamp name.
            ant.move( file:log, tofile:newFilename );
            // Then we move the file to the output directory.
            if(!".".equals(outputDir)) {
                ant.move( file:newFilename, todir:outputDir );
            }
            println "The output is stored in " + 
                (new File(new File(outputDir), newFilename)).canonicalPath;
        }
        if(result != 0) {
            println "Error: Running the Xcrun command failed with error code: " + result;
            if(timeout) {
                println "The timeout may have been exceeded."
            }
        }
        return result;
    }
    
    /**
    * Runs the xcodebuild command for unit testing with the provided 
    * options. Uses the xcrun command to find the tool. Use 
    * xcode-select to set the system default for developer tools.
    * See
    * https://developer.apple.com/library/ios/documentation/DeveloperTools/Conceptual/testing_with_xcode/A2-command_line_testing/A2-command_line_testing.html#//apple_ref/doc/uid/TP40014132-CH9-SW1
    * https://developer.apple.com/library/mac/documentation/Darwin/Reference/ManPages/man1/xcodebuild.1.html
    * xcrunPath: An optional path to the xcrun tool.
    * message: An optional message to output when the command is run.
    * arguments: The arguments to run the command with.
    * timeout: A period after which the xcrun command is stopped in
    *    milliseconds.
    * Returns the exit code of the command.
    **/
    public static int unitTest(def xcrunPath, def message, def arguments, 
        def timeout) {
        int result = runXcrunCmd(message, arguments, xcrunPath,
            timeout) { builder ->
            def log = builder.toString();
            // Output the log to the console.
            println log;
        }
        
        if(result != 0) {
            println "Error: Running the unit test command failed with error " +
                "code: ${result}"; 
            if(timeout) {
                println "The timeout may have been exceeded."
            }
        }
        return result;
    }
    
    /**
    * Configures the xcrun command for command helper including the 
    * path to xcrun and the arguments to use.
    * xcrunPath: An optional path to the xcrun command, for example, 
    *   /usr/bin. A null value can be provided for the default system
    *   location.
    * arguments: The arguments to the xcrun command.
    * Returns the command to be used by command helper as an argument
    *    array.
    **/
    private static def setupXcrunCmd(def xcrunPath, def arguments) {
        def args = [];
        
        if(xcrunPath) {
            def xcrunCmd;
            try {
                xcrunCmd = new File(xcrunPath + File.separator + "xcrun");
            } catch (Exception e) {
                println "An error occurred during an attempt to access the Xcrun Application: " +
                    e.getMessage();
                System.exit(-1);
            }
            if(!xcrunCmd.file) {
                println "Error: The path to the Xcrun Application is incorrect: " + 
                    xcrunCmd.canonicalPath;
                System.exit(-1);
            }
            args << xcrunCmd.canonicalPath;
        } else {
            args << "xcrun";
        }
        
        if(!arguments) {
            println "Error: Arguments must be provided for the xcrun command.";
            System.exit(-1);
        }
        args = args.plus(args.size(), arguments);
        
        return args;
    }
    
    /**
    * Takes the path to the Xcode application install, verifies the path, and
    * returns a File reference to the location.
    * xcode: The path to the Xcode install to verify.
    * Returns a File reference to the Xcode install.
    **/
    public static def verifyXcodePath(def xcode) {
        def xcodeApp;
        try {
            xcodeApp = new File(xcode);
        } catch (Exception e) {
            println "An error occurred during an attempt to access the Xcode Application" +
                "directory: " + e.getMessage();
            System.exit(-1);
        }
        if(!xcodeApp.directory) {
            println "Error: The path to the Xcode Application is incorrect: " + 
                xcodeApp.canonicalPath;
            System.exit(-1);
        }
        return xcodeApp;
    }
    
    /**
    * Runs the mobiledevice command for finding an application's bundle ID. 
    * app: The path to the application (.app).
    * udid: The optional device identifier. Only one of udid or name can be provided.
    * name: The optional name of the device. Only one of udid or name can be provided.
    * timeout: A period after which the xcrun command is stopped in
    *    milliseconds.
    * Returns the bundle ID of the application or null on error.
    **/
    public static String checkDeviceBundle(def app, def udid, def name, def timeout) {
        def args = ['get_bundle_id'];
        args << handleApplication(app);
        
        String bundle;
        int result = runDeviceCmd("Obtaining application bundle ID.", args, udid,
            name, timeout) { builder ->
            bundle = builder.toString();
            if(!bundle?.trim()) {
                println "Error: Failed to find the bundle ID for the provided application.";
                System.exit(-1);
            }
        }
        if(result) {
            return bundle;
        } else {
            return null;
        }
    }
    
    /**
    * Runs the mobiledevice command for installing an application on a device. 
    * app: The path to the application (.ipa or .app) to install.
    * udid: The optional device identifier. Only one of udid or name can be provided.
    * name: The optional name of the device. Only one of udid or name can be provided.
    * timeout: A period after which the xcrun command is stopped in
    *    milliseconds.
    * Returns the status of the removal command.
    **/
    public static int installDeviceApp(def app, def udid, def name, def timeout) {
        def args = ['install_app'];
        args << handleApplication(app);
        
        String output;
        int result = runDeviceCmd("Installing application: " + app , args, 
            udid, name, timeout) { builder ->
            output = builder.toString();
            if("OK" != output?.trim()) {
                println "Error: Failed to install the application on the device.";
                println "Explanation: This error can occur if the application is not " +
                    "built for the correct target.";
                println "User response: Verify the application is built for the correct " +
                    "target (for example, iphoneos or iphonesimulator) and architecture " +
                    "(for example, 64-bit).";
                System.exit(-1);
            }
        }
        return result;
    }
    
    /**
    * Runs the mobiledevice command for finding an application on a device. 
    * app: The name (.app) or bundle ID of the application (.ipa or .app) to find.
    * isPkg: Boolean whether the supplied app is being searched by package.
    * udid: The optional device identifier. Only one of udid or name can be provided.
    * name: The optional name of the device. Only one of udid or name can be provided.
    * timeout: A period after which the xcrun command is stopped in
    *    milliseconds.
    * Returns whether the application was found.
    **/
    public static boolean findDeviceApp(def app, def isPkg, def udid, def name, def timeout) {
        def args = ['list_installed_apps'];
        def appToFind;
        if(isPkg) {
            if(!app?.trim()) {
                println "Error: A package must be supplied for location on the device.";
                System.exit(-1);
            }
            appToFind = app.trim();
        } else {
            args << "-p";
            appToFind = handleApplication(app);
            // Trim down to the app name.
            appToFind = appToFind.substring(appToFind.lastIndexOf(File.separator));
        }
        
        def found = false;
        int result = runDeviceCmd("Finding application: " + app , args, 
            udid, name, timeout) { builder ->
            builder.toString().eachLine { it ->
                if(isPkg) {
                    if(it == appToFind) {
                        found = true;
                    }
                } else {
                    if(it.substring(it.lastIndexOf(File.separator)) == appToFind) {
                        found = true;
                    }
                }
            }
        }
        if(result !=0) {
            System.exit(-1);
        }
        return found;
    }
    
    /**
    * Runs the mobiledevice command for removing an application from a device. 
    * bundleID: The bundle ID of the application to remove.
    * udid: The optional device identifier. Only one of udid or name can be provided.
    * name: The optional name of the device. Only one of udid or name can be provided.
    * timeout: A period after which the xcrun command is stopped in
    *    milliseconds.
    * Returns the status of the removal command.
    **/
    public static int removeDeviceApp(def bundleID, def udid, def name, def timeout) {
        def args = ['uninstall_app'];
        if(!bundleID?.trim()) {
            println "Error: An application bundle ID must be provided for application " +
                "removal.";
            System.exit(-1);
        }
        boolean isInstalled = Util.findDeviceApp(bundleID, true, udid, null, timeout);
        if(!isInstalled) {
            println "Error: The application with bundle ID ${bundleID} is not installed.";
            System.exit(-1);
        }
        args << bundleID;
        
        String output;
        int result = runDeviceCmd("Removing application with bundle ID: " + bundleID , args, 
            udid, name, timeout) { builder ->
            output = builder.toString();
            if("OK" != output?.trim()) {
                println "Error: Failed to remove the application from the device.";
                System.exit(-1);
            }
        }
        return result;
    }
    
    /**
    * Runs the command for removing an application from a simulator.
    * bundleID: The bundle ID of the application to remove.
    * udid: The unique device identifier of the simulator to check.
    * xcrunPath: An optional path to the xcrun tool.
    * Returns the status of the removal command.
    **/
    public static void removeSimulatorApp(def bundleID, def udid, def xcrunPath) { 
        if(!isSimulatorRunning(udid, xcrunPath)) {
            println "Error: The simulator must be running when uninstalling an app " +
                "from the simulator.";
            System.exit(-1);
        }
        
        if(!findSimulatorApp(bundleID, udid)) {
            println "Error: The application with bundle ID ${bundleID} is not installed.";
            System.exit(-1);
        }
        
        def args = ['simctl', 'uninstall', udid, bundleID];
        // If the app is not installed, no error occurs.
        int result = runXcrunCmd("Uninstalling the app from the simulator.", args, xcrunPath,
            null) { builder ->
            def log = builder.toString();
            // Output the log to the console.
            println log;
        }
        if(result != 0) {
            println "Error: Running the Xcrun command failed with error code: " + result;
            System.exit(-1);
        }
    }
    
    /**
    * Runs the mobiledevice command with the provided options. 
    * message: An optional message to output when the command is run.
    * arguments: The arguments to run the command with.
    * udid: The optional device identifier. Only one of udid or name can be provided.
    * name: The optional name of the device. Only one of udid or name can be provided.
    * timeout: A period after which the xcrun command is stopped in
    *    milliseconds.
    * closure: Used with the builder StringBuilder, this allows for parsing
    *    the command's output streams (out and err).
    * Returns the exit code of the command.
    **/
    public static int runDeviceCmd(def message, def arguments, def udid, def name, 
        def timeout, Closure closure) {
        
        def args = [];
        
        // The script file is located in the plug-in.
        final def PLUGIN_HOME = new File(System.getenv().get("PLUGIN_HOME"));
        def scriptLoc = PLUGIN_HOME.canonicalPath + File.separator + 'mobiledevice';
        args << scriptLoc;
        
        // We need to change the file permission of the script since it may
        // not have executable permissions.
        def ant = new AntBuilder();
        ant.chmod( file:scriptLoc, perm:"u+x" );
        
        if(!arguments) {
            println "Error: Arguments must be provided for the device command.";
            System.exit(-1);
        }
        args = args.plus(args.size(), arguments);
        
        if(udid?.trim()) {
            args << "-d" << udid.trim();
        } else if(name?.trim()) {
            args << "-n" << name.trim();
        }
        
        
        def ch = new CommandHelper(new File('.'));
        ch.ignoreExitValue = true;
        def result = ch.runCommand(message, args) {
            proc ->
            def builder = new StringBuilder()
            if(timeout) {
                // forward stdout and stderr for processing
                proc.consumeProcessOutput(builder, builder)
                println "A timeout of " + timeout + " milliseconds is enabled.";
                proc.waitForOrKill(Long.parseLong(timeout));
            } else {
                proc.waitForProcessOutput(builder, builder);
            }
            
            if(closure) {
                closure(builder);
            }
            
        }
        if(result != 0) {
            println "Error: Running the device command failed with error code: " + result;
            if(timeout) {
                println "The timeout may have been exceeded."
            }
        }
        return result;
    }
    
    /**
    * Determines if the supplied application is installed on the simulator. 
    * bundleID: The bundle ID of the application to find.
    * udid: The unique device identifier of the simulator to check.
    * Returns whether the application was found.
    **/
    public static boolean findSimulatorApp(def bundleID, def udid) {
        def simDir = getSimulatorPath(udid);
        try {
            simDir = new File(simDir, "data" + File.separator + "Applications");
        } catch (Exception e) {
            println "An error occurred during an attempt to access the simulator " + 
                    "application directory: " + e.getMessage();
            System.exit(-1);
        }
        
        if(simDir == null || !simDir.isDirectory()) {
            //iOS 8 application location
            try {
                simDir = getSimulatorPath(udid);
                simDir = new File(simDir, "data" + File.separator + "Containers" + 
                    File.separator + "Data" + File.separator + "Application");
            } catch (Exception e) {
                println "An error occurred during an attempt to access the simulator " +
                        "application directory: " + e.getMessage();
                System.exit(-1);
            }

            if(simDir == null || !simDir.isDirectory()){
                println "The simulator application directory was not found.";
                return false;
            }
        }
        
        def appFound = false;
        def ch = new CommandHelper(new File('.'));
        ch.ignoreExitValue(true);
        simDir.eachDir { uuidDir ->
            uuidDir.eachDir { appDir ->
                appDir.eachFile { infoFile ->
                    if (infoFile.name == "Info.plist") {
                        def infoPath = appDir.canonicalPath + File.separator + 'info';
                        def args = ['defaults', 'read', infoPath, 'CFBundleIdentifier'];
                        ch.runCommand("read app bundle ID", args) { proc ->
                            InputStream inStream =  proc.getInputStream();
                            inStream.eachLine { line ->
                                if (line == bundleID){
                                    appFound = true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return appFound;
    }
    
    /**
    * Determines the UDID of the simulator.
    * simName: The simulator configuration name to check.
    * simDeviceType: The simulator device type (e.g: iPhone 5s).
    * targetOS: The OS of the simulator to find the target OS with version. If the
    *   version is an empty String, the latest version is returned. This is used
    *   in the Unit test scenario.
    * xcrunPath: An optional path to the xcrun tool.
    * Returns UDID of the specified simulator.
    **/
    public static String findSimulatorUDID(def simName, def simDeviceType, def targetOS, def xcrunPath) {
        def udid;
        boolean findSimDeviceType = false;
        def ch = new CommandHelper(new File('.'));
        ch.ignoreExitValue(true);
        def args = ['instruments', '-s', 'devices'];
        int result = runXcrunCmd("Finding the simulator UDID.", args, xcrunPath,
            null) { builder ->
            def log = builder.toString();
            // Output the log to the console.
            println log;
            /*
            * Determine if the simulator name is in the list. The simulator entry 
            * starts at the beginning of the line with the name, and is followed by 
            * the target OS version, including the maintenance version. It is possible
            * that the name could include characters that need to be escaped, so we 
            * quote the entries provided by the user.
            *
            * The target OS version can be empty when running a Unit Test, since the
            * user could be targeting the latest OS. In this case, we look for the
            * simulator name only.
            *
            */
            if(targetOS.length() == 0) {
                // The entries should be sorted, so we take the last one with the
                // corresponding simulator name.
                def entries = log.findAll(/\Q${simName.trim()}\E\s\(\d\.\d(\.\d)?\sSimulator\).*/);
                log = entries.get(entries.size()-1);
            } else {             
                log = log.findAll(/\Q${simName.trim()}\E\s\(\Q${targetOS.trim()}\E(\.\d)?\sSimulator\).*/);
                
                if (log.size() > 1) {
                    if(simDeviceType.length() == 0){
                        println "There are more than one simulators with the same target OS were found, " +
                        "please provid a simulator device type.";
                        System.exit(-1);
                    } else {       
                        for (int i = 0; i < log.size(); i ++){
                            def udidTmp = log.get(i).split("\\[")[1];
                            udidTmp = udidTmp.split("\\]")[0];
                            
                            // Determine the simulator device type.
                            def deviceInfo = getSimulatorPath(udidTmp);
                            try {
                                deviceInfo = new File(deviceInfo, "device.plist");
                            } catch (Exception e) {
                                println "An error occurred during an attempt to access the simulator device " +
                                    "plist file: " + e.getMessage();
                                System.exit(-1);
                            }
                            
                            if (deviceInfo == null || !deviceInfo.file){
                                println "Error: Not able to find the simulator device plist file: " +
                                        deviceInfo.canonicalPath;
                                System.exit(-1);
                            }
                            def deviceType;
                            args = ['defaults', 'read', deviceInfo, 'deviceType'];
                            ch.runCommand("Check simulator device type.", args) { proc ->
                                InputStream inStream =  proc.getInputStream();
                                List lines = inStream.readLines();
                                if(lines.size == 0) {
                                    println "The simulator device type was not found.";
                                    System.exit(-1);
                                }
                                //The first line is the device type (ignore the new line).
                                deviceType = lines.get(0);
                            }
                            println "The simulator device type to check: " + deviceType;
                            
                            //compare the simulator device type with user input.
                            def sdt = simDeviceType.replaceAll(' ', '-');
                            sdt = "com.apple.CoreSimulator.SimDeviceType."+sdt;
                            if (deviceType == sdt){
                                log = log.get(i);
                                findSimDeviceType = true;
                                break;
                            }
                        }
                    }
                    if(!findSimDeviceType) {
                        println "Error: The simulator with simulator name " + simName + 
                            ", target OS " + targetOS + " and simulator device type " +
                            simDeviceType + " could not be found.";
                        println "Explanation: This error can occur if simulator device type " +
                            "is incorrect.";
                        println "User response: Verify the simulator device type " +
                            "is correct.";
                        System.exit(-1);
                    }                    
                } else if (log.size() == 1){
                    log = log.get(0);
                }
            }
            
            if(log == null) {
                println "Error: The simulator with simulator name " + simName + 
                    " and target OS " + targetOS + " could not be found.";
                println "Explanation: This error can occur if the simulator name or " +
                    "target OS are incorrect.";
                println "User response: Verify the simulator name and " +
                    "target OS are correct.";
                System.exit(-1);
            }

            udid = log.split("\\[")[1];
            udid = udid.split("\\]")[0];
            println "The simulator UDID is: " + udid;
        }
        if(result != 0) {
            println "Error: Running the Xcrun command failed with error code: " + result;
            System.exit(-1);
        }
        return udid;
    }

    /**
    * Finds the bundle ID of an application given the path to the .app.
    * pathToApp: The local path to the .app directory to get the bundle ID for.
    * Returns the bundle ID from reading the Info.plist file or null if one is
    *   is not found.
    **/
    public static String getAppBundleID(def pathToApp) {
        def appDir;
        try {
            appDir = new File(pathToApp);
        } catch (Exception e) {
            println "An error occurred when attempting to get the application's " +
                "bundle ID: " + e.getMessage();
            System.exit(-1);
        }
        if(!appDir?.isDirectory()) {
            println "The path to the application was not found.";
            System.exit(-1);
        }
        def bundleID = null;
        def ch = new CommandHelper(new File('.'));
        ch.ignoreExitValue(true);
        appDir.eachFile { infoFile ->
            if (infoFile.name == "Info.plist") {
                def infoPath = appDir.canonicalPath + File.separator + 'info';
                def args = ['defaults', 'read', infoPath, 'CFBundleIdentifier'];
                ch.runCommand("read app bundle ID", args) { proc ->
                    InputStream inStream =  proc.getInputStream();
                    List lines = inStream.readLines();
                    if(lines.size == 0) {
                        println "An error occurred finding the bundle ID.";
                        System.exit(-1);
                    }
                    //The first line is the bundle ID (ignore the new line).
                    bundleID = lines.get(0);
                }
            }
        }
        return bundleID;
    }
    
    /**
    * Builds up the path to the target simulator installation.
    * udid: The unique device identifier of the simulator to check.
    * Returns the path to the target simulator installation.
    **/
    private static File getSimulatorPath(def udid) {
        def simDir;
        try {
            simDir = new File(System.getProperty("user.home") + File.separator + 
                "Library" + File.separator + "Developer" + File.separator + 
                "CoreSimulator" + File.separator + "Devices" + File.separator + udid);
        } catch (Exception e) {
            println "An error occurred during an attempt to access the simulator: " +
                e.getMessage();
            System.exit(-1);
        }
        
        if (simDir == null || !simDir.isDirectory()) {
            println "Error: Not able to find the simulator directory: " + 
                    simDir.canonicalPath;
            System.exit(-1);
        }
        println "The simulator path is: " + simDir.canonicalPath;
        return simDir;
    }
    
    /**
    * Installs the supplied application on the target simulator. 
    * app: The path to the application (.ipa or .app) to install.
    * udid: The unique device identifier of the simulator to check.
    * xcrunPath: An optional path to the xcrun tool.
    * Returns the status of the install command.
    **/
    public static void installSimulatorApp(def app, def udid, def xcrunPath) {
        if(!isSimulatorRunning(udid, xcrunPath)) {
            println "Error: The simulator must be running when installing an app " +
                "on the simulator.";
            System.exit(-1);
        }
        
        def args = ['simctl', 'install', udid, app];
        // If the app is already installed, then no message appears. The simulator must be running.
        int result = runXcrunCmd("Installing the app on the simulator.", args, xcrunPath,
            null) { builder ->
            def log = builder.toString();
            // Output the log to the console.
            println log;
        }
        if(result != 0) {
            println "Error: Running the Xcrun command failed with error code: " + result;
            System.exit(-1);
        }
    }

    /**
    * Runs the xcrun command with the provided options.
    * message: An optional message to output when the command is run.
    * arguments: The arguments to run the command with.
    * xcrunPath: An optional path to the xcrun tool.
    * timeout: A period after which the xcrun command is stopped in
    *    milliseconds.
    * closure: Used with the builder StringBuilder, this allows for parsing
    *    the command's output streams (out and err).
    * Returns the exit code of the command.
    **/
    private static int runXcrunCmd(def message, def arguments, def xcrunPath,
        def timeout, Closure closure) {
        def args = setupXcrunCmd(xcrunPath, arguments);
        def ch = new CommandHelper(new File('.'));
        ch.ignoreExitValue = true;
        def result = ch.runCommand(message, args) {
            proc ->
            def builder = new StringBuilder()
            if(timeout) {
                // forward stdout and stderr for processing
                proc.consumeProcessOutput(builder, builder)
                println "A timeout of " + timeout + " milliseconds is enabled.";
                proc.waitForOrKill(Long.parseLong(timeout));
            } else {
                proc.waitForProcessOutput(builder, builder);
            }
            
            if(closure) {
                closure(builder);
            }
        }
        return result;
    }
    
    /**
    * Runs the xcrun command with the provided optional arguments.
    * message: An optional message to output when the command is run.
    * arguments: A space-separated list or property file of arguments.
    * xcrunPath: An optional path to the xcrun tool.
    * timeout: A period after which the Android command is stopped in
    *    milliseconds.
    **/
    public static int xcrunCmd(def message, def arguments, def xcrunPath, def timeout) {
        def args = [];
        if(arguments) {
            args = handleArgs(arguments, args);
        }
        int result = runXcrunCmd(message, args, xcrunPath,
            timeout) { builder ->
            def log = builder.toString();
            // Output the log to the console.
            println log;
        }
        
        if(result != 0) {
            println "Error: Running the Xcrun command failed with error code: " + result;
            if(timeout) {
                println "The timeout may have been exceeded."
            }
        }
        return result;
    }
    
    /**
    * Handles adding the space-separated list or file of optional arguments.
    * arguments: A space-separated list or property file of arguments.
    * arg: The existing arguments for the command to which to append the passed
    *     in arguments.
    * Returns an updated list of arguments for the command.
    **/
    public static def handleArgs(String arguments, def args) {
        
        // Check if the argument is a file or arguments
        def inFile = new File(arguments);
        
        if(inFile.file) {
            arguments = inFile.getText();
            System.out.println("Reading arguments from: " + inFile.getCanonicalFile());
        }
        
        // Add the passed in arguments to the existing args.
        arguments.eachLine { it ->
            if(it?.trim()) {
                args << it.trim()
            }
        }
        return args;
    }
}
