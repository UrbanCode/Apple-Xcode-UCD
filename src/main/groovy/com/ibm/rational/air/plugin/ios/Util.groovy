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
    * target: The OS of the simulator with architecture option (e.g: 7.0-64).
    * targetOS: The OS of the simulator without architecture option (e.g: 7.0).
    * targetOSWithVersion: The OS of the simulator with version without architecture option (e.g: 7.0.3).
    * simType: The simulator type (e.g: 'iPhone Retina (4-inch 64-bit)', 'iPhone Retina (4-inch)').
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
        def args = ['pgrep', 'iPhone Simulator'];
        // The exit value is 0 for when a simulator is running, 1 otherwise.
        return !ch.runCommand("Checking simulator status.", args);
    }
    
    /**
    * Get the target OS version 
    * targetOS: The OS of the simulator to find the target OS with version
    *   (without architecture option e.g 7.0).
    * xcode: The path to Xcode used for finding the simulator.
    **/
    private static String getTargetOSVersion(def targetOS, def xcode) {
        def xcodeApp = Util.verifyXcodePath(xcode);
        try {
            def pathToVersion = new String(File.separator + "Contents" + File.separator +
                "Developer" + File.separator + "Platforms" + File.separator +
                "iPhoneSimulator.platform" + File.separator + "Developer" + File.separator +
                "SDKs" + File.separator + "iPhoneSimulator" + targetOS + ".sdk" + File.separator +
                "System" + File.separator + "Library" + File.separator + "CoreServices" +
                File.separator +"SystemVersion.plist");
            xcodeApp = new File(xcodeApp, pathToVersion);
        } catch (Exception e) {
            println "An error occurred during an attempt to access the SystemVersion.plist file of target OS " +
                targetOS;
            println  "Exception message:" + e.getMessage();
            System.exit(-1);
        }
        
        def targetOSWithVersion = xcodeApp.getText();
        if(targetOSWithVersion == null || targetOSWithVersion.trim().length() == 0) {
            println "Error: the SystemVersion.plist file is empty.";
            System.exit(-1);
        }
        
        targetOSWithVersion = targetOSWithVersion.find(/ProductVersion.*\n.*/);
        targetOSWithVersion = targetOSWithVersion.find(/(\d+\.)+\d+/);
        println "The target OS version is " + targetOSWithVersion;
        return targetOSWithVersion;
    }
    
    /**
    * Checks whether a simulator started based on a maximum retry number for each
    *    10-second interval.
    * startupRetries: The polling frequency. That is, how many times to check every
    *    10 seconds for the simulator status.
    * simType: The simulator configuration type to check.
    * targetOS: The OS of the simulator to check (without architecture option e.g 7.0).
    * xcode: The path to Xcode used for checking the simulator.
    **/
    public static void waitForSimulator(int startupRetries, def simType, def targetOS, def xcode) {
        def targetOSWithVersion = getTargetOSVersion(targetOS, xcode);
        
        def archLevel = "";
        if (simType.contains("64-bit"))
            archLevel = "-64";
        def syslogPath;
        try {
            syslogPath = new File(System.getProperty("user.home") + File.separator + "Library" +
                File.separator + "Logs" + File.separator + "iOS Simulator" +
                File.separator + targetOSWithVersion + archLevel + File.separator + "system.log");
        } catch (Exception e) {
            println "An error occurred during an attempt to access the simulator log file: " +
                e.getMessage();
            System.exit(-1);
        }
        
        //If the iOS Simulator system.log file is not found (e.g any OS level lower than 7.0),
        //check the system level log file.
        if (!syslogPath.file){
            println "Not able to find the iOS Simulator log file: " + 
                    syslogPath.canonicalPath;
            println "Checking the system level log file."
            syslogPath = new File("/var/log/system.log");
            
            if (!syslogPath.file){
                println "Error: The path to the system level log file is incorrect: " +
                        syslogPath.canonicalPath;
                    System.exit(-1);
            }
        }
        
        def args = ['tail', '-n', '-0', '-F', syslogPath];
        def ch = new CommandHelper(new File('.'));
        boolean foundSimulator = false;
        int MAX_TRIES = startupRetries;
        int SLEEP = 10000;
        
        def builder = new StringBuilder();
        def errOut = new StringBuilder();
        // The process is interrupted when a simulator is found.
        // So we ignore the exit value since it will return 1.
        ch.ignoreExitValue(true);
        println "Waiting for the simulator to start.";
        try {
            ch.runCommand(null, args) {
                proc ->
                // store stdout and stderr for processing
                proc.consumeProcessOutput(builder, errOut)
                for(int i = 0; !foundSimulator && i < MAX_TRIES; i++) {
                    def devices = builder.toString();
                    if(devices.contains("SIMToolkit plugin for SpringBoard initialized")){
                        foundSimulator = true;
                    } else {
                        if (i != (MAX_TRIES-1))
                            Thread.sleep(SLEEP);
                    }
                }
                // End the process and report the status.
                proc.destroy();
            };
        } catch (Exception e) {
            println "An error occurred during an attempt to find a started simulator: ${e.message}";
            e.printStackTrace();
            System.exit(-1);
        }
        
        if(!foundSimulator) {
            println "Error: The simulator was not found. Please check the system.";
            System.exit(-1);
        }
        
        println "The simulator UI is ready.";
    }
    
    /**
    * Starts the simulator.
    * simType: The simulator configuration type to start.
    * targetOS: The OS of the simulator to start (without architecture option e.g 7.0).
    * xcode: The path to Xcode used for starting the simulator.
    **/
    public static void startSimulator(def simType, def targetOS, def xcode) {
        // Update the device type and target SDK OS before launching the simulator.
        if(simType?.trim() && targetOS?.trim()) {
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
            def simArgs = ['osascript', scriptLoc, simType, xcodeApp.canonicalPath];
            
            simCH.runCommand("Updating the simulator configuration.", simArgs);
            
            def plistLoc = System.getenv().get("HOME") + File.separator + "Library" +
                File.separator + "Preferences" + File.separator + "com.apple.iphonesimulator";
            simArgs = ['defaults', 'read', plistLoc];
            simCH.runCommand("Refreshing the simulator configuration.", simArgs);
             
            println "The simulator will launch an ${simType} using SDK ${targetOS}.";
        } else {
            if((simType && !targetOS ) || (!simType && targetOS)) {
                println "Error: Both the Simulator Type and Target OS must be specified when changing " +
                    "the simulator configuration.";
                System.exit(-1);
            }
        } 
        
        // Start the simulator.
        def ch = new CommandHelper(new File('.'));
        def args = ['osascript', '-e', 'tell application \"iPhone Simulator\" to launch'];
        
        ch.runCommand("Starting the simulator.", args);
    }
    
    /**
    * Stops the running simulator.
    **/
    public static void stopSimulator() {
        def ch = new CommandHelper(new File('.'));
        def args = ['osascript', '-e', 'tell application \"iPhone Simulator\" to quit'];

        ch.runCommand("Stopping the simulator.", args);
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
                System.exit(-1);
            }
        }
        return result;
    }
    
    /**
    * Runs the mobiledevice command for finding an application on a device. 
    * app: The name (.app) or package of the application (.ipa or .app) to find.
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
    * target: The OS platform target where the application is installed (with architecture option e.g 7.0-64).
    * xcode: The path to Xcode used for starting the simulator.
    * Returns the status of the removal command.
    **/
    public static void removeSimulatorApp(def bundleID, def target, def xcode) { 
        def appFound = false;
        def simDir = getSimulatorPath(target, xcode);
        if(!simDir.isDirectory()) {
            println "Error: The path to the simulator is incorrect: " +
                simDir.canonicalPath;
            System.exit(-1);
        }
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
                                    println "Uninstalling the app from the simulator. ";
                                    uuidDir.deleteDir();
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!appFound){
            println "Error: The application with bundle ID ${bundleID} is not installed.";
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
    * app: The name (.app) or package of the application (.ipa or .app) to find.
    * isPkg: Boolean whether the supplied app is being searched by package. Not currently supported.
    * target: The OS simulator target to check for the installed application (with architecture option e.g: 7.0-64).
    * xcode: The path to Xcode used for finding the simulator application.
    * Returns whether the application was found.
    **/
    public static boolean findSimulatorApp(def appName, def isPkg, def target, def xcode) {
        def simDir = getSimulatorPath(target, xcode);
        if(!simDir.isDirectory()) {
            println "The path to the simulator was not found.";
            return false;
        }
        def appFound = false;
        simDir.eachDir { uuidDir ->
            uuidDir.eachDir { appDir ->
                if(appDir.name == appName) {
                    appFound = true;
                }
            }
        }
        return appFound;
    }
    
    /**
    * Finds the path to the target simulator installation.
    * target: The OS platform target to install the application into (with architecture option e.g: 7.0-64).
    * xcode: The path to Xcode used for getting the simulator path.
    * Returns the File path to the target simulator installation.
    **/
    private static File getSimulatorPath(def target, def xcode) {
        if(!target?.trim()) {
            println "Error: A target OS must be specified.";
            System.exit(-1);
        }
        
        def archLevel = ""; // architecture level can be -64 or an empty string
        def targetOS = target; // the OS platform target without the architecture option
        if(target.contains("-64")){
            archLevel = "-64";
            targetOS = target.substring(0, target.indexOf("-64"));
        }
        def targetOSWithVersion = getTargetOSVersion(targetOS, xcode); //e.g return 7.0.3
        targetOSWithVersion += archLevel; //e.g return 7.0.3-64
        
        // Build up the directory to the install and verify the directories exist.
        def simDir;
        try {
            simDir = new File(System.getProperty("user.home") + File.separator + "Library" +
                File.separator + "Application Support" + File.separator + "iPhone Simulator" +
                File.separator + targetOSWithVersion + File.separator + "Applications");
        } catch (Exception e) {
            println "An error occurred during an attempt to access the simulator: " +
                e.getMessage();
            System.exit(-1);
        }
        if(simDir == null){
            println "Error: the simulator directory was not found."
            System.exit(-1);
        }
        return simDir;
    }
    
    /**
    * Create a simulator directory for a given target OS:
    * targetOS: The OS platform target to install the application into (without architecture option e.g: 7.0).
    * xcode: The path to Xcode used for starting the simulator.
    * is64Bit: the architecture option
    **/
    private static void createSimulatorDir(def targetOS, def xcode, boolean is64Bit){
        println "Creating a simulator directory for target OS: " + targetOS;
        //Starting simulator;
        def simulatorType;
        if(is64Bit) {
            simulatorType = "iPhone Retina (4-inch 64-bit)";
        } else {
            simulatorType = "iPhone Retina (4-inch)";
        }
        // Only one simulator can run at a time.
        if(isSimulatorRunning()) {
            println "A simulator is already running.";
            System.exit(-1);
        }
        startSimulator(simulatorType, targetOS, xcode);
        if(!Util.isSimulatorRunning()) {
            println "The simulator failed to start.";
            System.exit(-1);
        }
        waitForSimulator(10, simulatorType, targetOS, xcode);
        
        //Stopping simulator;
        if(!isSimulatorRunning()) {
            println "No simulator was running.";
            System.exit(-1);
        }
        Util.stopSimulator();
        if(Util.isSimulatorRunning()) {
            println "The simulator failed to stop during simulator creation "+
                    "of the target OS ${targetOS}.";
            System.exit(-1);
        }
        println "The simulator has stopped.";
        
        println "The simulator directory is created";
    }
    
    /**
    * Installs the supplied application on the target simulator. 
    * app: The path to the application (.ipa or .app) to install.
    * target: The OS platform target to install the application into (with architecture option e.g. 7.0-64).
    * xcode: The path to Xcode used for starting the simulator.
    * Returns the status of the install command.
    **/
    public static void installSimulatorApp(def app, def target, def xcode) {
        boolean is64Bit = false;
        def targetOS = target; // the OS platform target without the architecture option
        if(target.contains("-64")){
            targetOS = target.substring(0, target.indexOf("-64"));
            is64Bit = true;
        }
        def simDir = getSimulatorPath(target, xcode);
        if(!simDir.isDirectory()) {
            //If the simulator directory doesn't exist, we try to create the directory.
            createSimulatorDir(targetOS, xcode, is64Bit);
        }
        println "Installing into simulator: " + simDir.canonicalPath;
        
        // Generate a UUID as the application installation directory.
        def uuid = UUID.randomUUID().toString();
        try {
            simDir = new File(simDir, uuid);
        } catch (Exception e) {
            println "An error occurred during an attempt to setup the application " +
                "directory: " + e.getMessage();
            System.exit(-1);
        }
        
        // Create the structure for the application in the simulator.
        if(!simDir.mkdir()) {
            println "An error occurred during an attempt to create the application " +
                "directory: " + simDir.canonicalPath;
            System.exit(-1);
        }
        
        def docDir;
        try {
            docDir = new File(simDir, "Documents");
        } catch (Exception e) {
            println "An error occurred during an attempt to setup the application " +
                "Documents directory: " + e.getMessage();
            System.exit(-1);
        }
        if(!docDir.mkdir()) {
            println "An error occurred during an attempt to create the application " +
                "Documents directory: " + docDir.canonicalPath;
            System.exit(-1);
        }
        
        def libDir;
        try {
            libDir = new File(simDir, "Library");
        } catch (Exception e) {
            println "An error occurred during an attempt to setup the application " +
                "Library directory: " + e.getMessage();
            System.exit(-1);
        }
        if(!libDir.mkdir()) {
            println "An error occurred during an attempt to create the application " +
                "Library directory: " + libDir.canonicalPath;
            System.exit(-1);
        }
        
        def tmpDir;
        try {
            tmpDir = new File(simDir, "tmp");
        } catch (Exception e) {
            println "An error occurred during an attempt to setup the application " +
                "tmp directory: " + e.getMessage();
            System.exit(-1);
        }
        if(!tmpDir.mkdir()) {
            println "An error occurred during an attempt to create the application " +
                "tmp directory: " + tmpDir.canonicalPath;
            System.exit(-1);
        }
        
        println "The application will be installed into: " + simDir.canonicalPath;
        
        // Copy the .app file to the simulator directory to retain permissions.
        def appPath = handleApplication(app)
        def ch = new CommandHelper(new File('.'));
        def args = ['cp', '-r', appPath, simDir];

        def result = ch.runCommand("Installing the app on the simulator.", args);
        if(result != 0) {
            println "Error: Running the copy command to install the application into " +
                "the simulator failed with this error code: ${result}.";
            println "Removing the attempted install.";
            simDir.deleteDir();
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
