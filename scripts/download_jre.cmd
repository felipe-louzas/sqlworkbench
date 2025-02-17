@echo off
echo This batchfile will download a Java 21 JRE (64bit) from https://www.sql-workbench.eu
echo to be used with SQL Workbench/J
echo.

if exist "%~dp0jre" (
  echo "A JRE directory already exists."
  echo "Please remove (or rename) this directory before running this batch file"
  goto :eof
)
set /P continue="Do you want to continue? (Y/N) "

for %%i in (y yes) do if /i [%%i]==[%continue%] goto make_jre


goto :eof

:make_jre
@powershell.exe -noprofile -executionpolicy bypass -file download_jre.ps1

setlocal

if exist jdk* for /d %%i in (jdk*) do @echo jdkdir exists: [%%~nxi] -^> renaming to [jre] & ren "%%i" jre

if Not exist jre (
  echo Error: No JRE created in %~dp0jre!
  pause
  goto :eof
)

set zipfile=

echo.
echo JRE created in %~dp0jre
echo.
if exist "OpenJDK*.zip" call :handle_zipfiles "OpenJDK*.zip"

goto :eof

:handle_zipfiles

 echo You can delete the ZIP archive [%~1] now.
 set /P continue="Do you want to delete now? (Y/N) "
 for %%i in (y yes) do if /i [%%i]==[%continue%] del %1 >nul && echo %1 deleted.
      
 echo.

