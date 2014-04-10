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
        
        // Extract the .app file
        if(app.name.endsWith(".ipa")) {
            def workingDir = File.createTempFile("ucdIOS", "");
            workingDir.delete();
            workingDir.mkdir();
            def ant = new AntBuilder();
            ant.unzip( src: app.canonicalPath, dest: workingDir.canonicalPath );
            def payloadDir = new File(workingDir, "Payload");
            payloadDir.eachFile { it ->
                if(it.name.endsWith(".app")) {
                    def curDir = new File('.');
                    println "Moving the following file to the working directory: " +
                        it.canonicalPath;
                    // Note: This will overwrite any existing .app in the working directory.
                    ant.move( file: it.canonicalPath, todir: curDir.canonicalPath );
                    app = new File(curDir, it.name);
                }
            }
            workingDir.delete();
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
}