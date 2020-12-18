@echo off
set JAVA_HOME=F:\Shared\Programmi\Java\jdk\15.0.1

del %~dp0org\burningwave\core\jvm\ClassLoaderDelegateForJDK9.bwc
del %~dp0org\burningwave\core\jvm\AccessibleSetterRetrieverForJDK16.bwc
del %~dp0org\burningwave\core\jvm\ConsulterRetrieverForJDK16.bwc
call %JAVA_HOME%\bin\javac.exe -cp "%~dp0..\..\..\target\classes;%~dp0;" --release 9 %~dp0jdk\internal\loader\ClassLoaderDelegateForJDK9.java
call %JAVA_HOME%\bin\javac.exe -cp "%~dp0..\..\..\target\classes;%~dp0;" --release 8 %~dp0java\lang\reflect\AccessibleSetterRetrieverForJDK16.java
call %JAVA_HOME%\bin\javac.exe -cp "%~dp0..\..\..\target\classes;%~dp0;" --release 8 %~dp0java\lang\invoke\ConsulterRetrieverForJDK16.java
move %~dp0jdk\internal\loader\ClassLoaderDelegateForJDK9.class %~dp0org\burningwave\core\jvm\ClassLoaderDelegateForJDK9.bwc
move %~dp0java\lang\reflect\AccessibleSetterRetrieverForJDK16.class %~dp0org\burningwave\core\jvm\AccessibleSetterRetrieverForJDK16.bwc
move %~dp0java\lang\invoke\ConsulterRetrieverForJDK16.class %~dp0org\burningwave\core\jvm\ConsulterRetrieverForJDK16.bwc
pause