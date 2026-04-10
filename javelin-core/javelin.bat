@echo off
@rem Javelin requires Java 17 or higher
setlocal

@rem Check common Java installation locations (prefer newer versions)
set "JAVA_HOME="

@rem Check for Java 22
for /d %%i in ("C:\Program Files\Java\jdk-22*") do set "JAVA_HOME=%%i"
if not defined JAVA_HOME for /d %%i in ("C:\Program Files\Eclipse Adoptium\jdk-22*") do set "JAVA_HOME=%%i"

@rem Check for Java 21
if not defined JAVA_HOME for /d %%i in ("C:\Program Files\Java\jdk-21*") do set "JAVA_HOME=%%i"
if not defined JAVA_HOME for /d %%i in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do set "JAVA_HOME=%%i"

@rem Check for Java 17
if not defined JAVA_HOME for /d %%i in ("C:\Program Files\Java\jdk-17*") do set "JAVA_HOME=%%i"
if not defined JAVA_HOME for /d %%i in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do set "JAVA_HOME=%%i"

call "%~dp0build\install\javelin\bin\javelin.bat" %*

endlocal
