(*
* Licensed Materials - Property of IBM Corp.
* IBM UrbanCode Deploy
* (c) Copyright IBM Corporation 2011, 2014. All Rights Reserved.
*
* U.S. Government Users Restricted Rights - Use, duplication or disclosure restricted by
* GSA ADP Schedule Contract with IBM Corp.
*)

on run argv

set deviceType to item 1 of argv as string
set sdkRoot to item 2 of argv as string


set PListDirPath to path to preferences folder from user domain as string
set PListPath to PListDirPath & "com.apple.iphonesimulator.plist"

tell application "System Events"
    tell property list file PListPath
        tell contents
            set value of property list item "currentSDKRoot" to sdkRoot
            set value of property list item "SimulateDevice" to deviceType
        end tell
    end tell
end tell

end run
