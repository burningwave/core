@echo off
set JAVA_HOME=F:\Shared\Programmi\Java\jdk\15.0.1

del %~dp0org\burningwave\core\jvm\ClassLoaderDelegateForJDK9.bwc
del %~dp0org\burningwave\core\jvm\AccessibleSetterRetrieverForJDK15.bwc
del %~dp0org\burningwave\core\jvm\ConsulterRetrieverForJDK15.bwc
call %JAVA_HOME%\bin\javac.exe -cp "%~dp0..\..\..\target\classes;%~dp0;" --release 9 %~dp0jdk\internal\loader\ClassLoaderDelegateForJDK9.java
call %JAVA_HOME%\bin\javac.exe -cp "%~dp0..\..\..\target\classes;%~dp0;" --release 8 %~dp0java\lang\reflect\AccessibleSetterRetrieverForJDK15.java
call %JAVA_HOME%\bin\javac.exe -cp "%~dp0..\..\..\target\classes;%~dp0;" --release 8 %~dp0java\lang\invoke\ConsulterRetrieverForJDK15.java
move %~dp0jdk\internal\loader\ClassLoaderDelegateForJDK9.class %~dp0org\burningwave\core\jvm\ClassLoaderDelegateForJDK9.bwc
move %~dp0java\lang\reflect\AccessibleSetterRetrieverForJDK15.class %~dp0org\burningwave\core\jvm\AccessibleSetterRetrieverForJDK15.bwc
move %~dp0java\lang\invoke\ConsulterRetrieverForJDK15.class %~dp0org\burningwave\core\jvm\ConsulterRetrieverForJDK15.bwc
pause