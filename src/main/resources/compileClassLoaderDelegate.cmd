@echo off
del %~dp0org\burningwave\core\classes\ClassLoaderDelegate.bwc
F:\Shared\Programmi\Java\jdk\13.0.1\bin\javac.exe -cp "%~dp0..\..\..\target\classes;%~dp0;" --release 9 %~dp0jdk\internal\loader\ClassLoaderDelegate.java
move %~dp0jdk\internal\loader\ClassLoaderDelegate.class %~dp0org\burningwave\core\classes\ClassLoaderDelegate.bwc
pause