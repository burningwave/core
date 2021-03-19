@echo off
set JAVA_HOME=F:\Shared\Programmi\Java\jdk\16

del %~dp0org\burningwave\core\jvm\*.bwc

call %JAVA_HOME%\bin\javac.exe -cp "%~dp0..\..\..\target\classes;%~dp0;" --release 9 %~dp0jdk\internal\loader\ClassLoaderDelegateForJDK9.java
call %JAVA_HOME%\bin\javac.exe -cp "%~dp0..\..\..\target\classes;%~dp0;" --release 8 %~dp0java\lang\reflect\AccessibleSetterInvokerForJDK9.java
call %JAVA_HOME%\bin\javac.exe -cp "%~dp0..\..\..\target\classes;%~dp0;" --release 8 %~dp0java\lang\ConsulterRetrieverForJDK9.java

move %~dp0jdk\internal\loader\ClassLoaderDelegateForJDK9.class %~dp0org\burningwave\core\jvm\ClassLoaderDelegateForJDK9.bwc
move %~dp0java\lang\reflect\AccessibleSetterInvokerForJDK9.class %~dp0org\burningwave\core\jvm\AccessibleSetterInvokerForJDK9.bwc
move %~dp0java\lang\ConsulterRetrieverForJDK9.class %~dp0org\burningwave\core\jvm\ConsulterRetrieverForJDK9.bwc

pause