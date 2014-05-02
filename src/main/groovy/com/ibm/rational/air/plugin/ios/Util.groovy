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
    
    /**
    * Starts the simulator.
    **/
    public static void startSimulator(def deviceType, def targetOS, def xcode) {
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
            
            def log = builder.toString();
            // Output the log to the console.
            println log;
            // Find out the output location
            log = log.find("Output : .*\\.trace");
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
    * target: The simulator target to check for the installed application.
    * Returns whether the application was found.
    **/
    public static boolean findSimulatorApp(def appName, def isPkg, def target) {
        def simDir = getSimulatorPath(target);
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
    * target: The OS platform target to install the application into (e.g. 7.0).
    * Returns the File path to the target simulator installation.
    **/
    private static File getSimulatorPath(def target) {
        if(!target?.trim()) {
            println "Error: A target OS must be specified.";
            System.exit(-1);
        }
        
        // Build up the directory to the install and verify the directories exist.
        def simDir;
        try {
            simDir = new File(System.getProperty("user.home") + File.separator + "Library" +
                File.separator + "Application Support" + File.separator + "iPhone Simulator" +
                File.separator + target + File.separator + "Applications");
        } catch (Exception e) {
            println "An error occurred during an attempt to access the simulator: " +
                e.getMessage();
            System.exit(-1);
        }
        if(!simDir.isDirectory()) {
            // The simulator directory doesn't exist, we can start/stop the simulator to
            // create the directory.
            // TODO: Start/stop the simulator?
            println "Error: The path to the simulator is incorrect: " + 
                simDir.canonicalPath;
            System.exit(-1);
        }
        return simDir;
    }
    
    /**
    * Installs the supplied application on the target simulator. 
    * app: The path to the application (.ipa or .app) to install.
    * target: The OS platform target to install the application into (e.g. 7.0).
    * Returns the status of the install command.
    **/
    public static void installSimulatorApp(def app, def target) {
        def simDir = getSimulatorPath(target);
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

        ch.runCommand("Installing the app on the simulator.", args);       
    }
}