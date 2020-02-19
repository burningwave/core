@echo off
del %~dp0org\burningwave\core\classes\ClassLoaderDelegate.bwclass
F:\Shared\Programmi\Java\jdk\9.0.1\bin\javac.exe -cp "%~dp0..\..\..\target\classes;%~dp0;" %~dp0jdk\internal\loader\ClassLoaderDelegate.java
move %~dp0jdk\internal\loader\ClassLoaderDelegate.class %~dp0org\burningwave\core\classes\ClassLoaderDelegate.bwclass
pause