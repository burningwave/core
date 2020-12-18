@echo off
set JAVA_HOME=F:\Shared\Programmi\Java\jdk\15.0.1

del %~dp0org\burningwave\core\jvm\ClassLoaderDelegate.bwc
del %~dp0org\burningwave\core\jvm\AccessibleSetterRetriever.bwc
del %~dp0org\burningwave\core\jvm\ConsulterRetriever.bwc
call %JAVA_HOME%\bin\javac.exe -cp "%~dp0..\..\..\target\classes;%~dp0;" --release 9 %~dp0jdk\internal\loader\ClassLoaderDelegate.java
call %JAVA_HOME%\bin\javac.exe -cp "%~dp0..\..\..\target\classes;%~dp0;" --release 8 %~dp0java\lang\reflect\AccessibleSetterRetriever.java
call %JAVA_HOME%\bin\javac.exe -cp "%~dp0..\..\..\target\classes;%~dp0;" --release 8 %~dp0java\lang\invoke\ConsulterRetriever.java
move %~dp0jdk\internal\loader\ClassLoaderDelegate.class %~dp0org\burningwave\core\jvm\ClassLoaderDelegate.bwc
move %~dp0java\lang\reflect\AccessibleSetterRetriever.class %~dp0org\burningwave\core\jvm\AccessibleSetterRetriever.bwc
move %~dp0java\lang\invoke\ConsulterRetriever.class %~dp0org\burningwave\core\jvm\ConsulterRetriever.bwc
pause