/*
* The settings file (settings.gradle(.kts)) is the entry point 
* of every Gradle project.
* The primary purpose of the settings file is to define the project structure, usually adding subprojects to your build. Therefore in:
* Single-project builds, the settings file is optional.
* Multi-project builds, the settings file is mandatory and declares all subprojects.
*/


// Name of the whole build, shown in the IDE and in build output
rootProject.name = "ftb-trading-platform"


/*
 * Every service must be listed here.
 * A folder that isn't included is invisible to Gradle.
 */

include("static-data-service")
