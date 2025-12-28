@rem Minimal Gradle Wrapper starter for Windows
@echo off
setlocal

set APP_HOME=%~dp0
set JAVA_EXE=java
if not "%JAVA_HOME%"=="" (
  if exist "%JAVA_HOME%\bin\java.exe" set JAVA_EXE="%JAVA_HOME%\bin\java.exe"
)

%JAVA_EXE% -classpath "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
