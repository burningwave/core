@echo off
del %~dp0jdk\internal\loader\ClassLoaderDelegate.bwclass
F:\Shared\Programmi\Java\jdk\9.0.1\bin\javac.exe -cp "%~dp0..\..\..\target\classes;%~dp0;" %~dp0jdk\internal\loader\ClassLoaderDelegate.java
ren %~dp0jdk\internal\loader\ClassLoaderDelegate.class ClassLoaderDelegate.bwclass
pause