@echo off
@rem Wrapper to run javelin from the javelin-cli root directory.
@rem Delegates to the installDist binary in javelin-core.
call "%~dp0javelin-core\build\install\javelin\bin\javelin.bat" %*
