@echo off
del %~dp0org\burningwave\core\classes\ClassLoaderDelegate.bwc
del %~dp0org\burningwave\core\classes\AccessibleSetterRetriever.bwc
del %~dp0org\burningwave\core\classes\ConsulterRetriever.bwc
F:\Shared\Programmi\Java\jdk\13.0.1\bin\javac.exe -cp "%~dp0..\..\..\target\classes;%~dp0;" --release 9 %~dp0jdk\internal\loader\ClassLoaderDelegate.java
F:\Shared\Programmi\Java\jdk\13.0.1\bin\javac.exe -cp "%~dp0..\..\..\target\classes;%~dp0;" --release 9 %~dp0jdk\internal\AccessibleSetterRetriever.java
F:\Shared\Programmi\Java\jdk\13.0.1\bin\javac.exe -cp "%~dp0..\..\..\target\classes;%~dp0;" --release 9 %~dp0jdk\internal\ConsulterRetriever.java
move %~dp0jdk\internal\loader\ClassLoaderDelegate.class %~dp0org\burningwave\core\classes\ClassLoaderDelegate.bwc
echo.
echo.
echo RENAME MANUALLY PACKAGE OF AccessibleSetterRetriever.class TO java.lang.reflect AND PACKAGE OF ConsulterRetriever.class TO java.lang.invoke
echo.
pause
move %~dp0jdk\internal\AccessibleSetterRetriever.class %~dp0org\burningwave\core\classes\AccessibleSetterRetriever.bwc
move %~dp0jdk\internal\ConsulterRetriever.class %~dp0org\burningwave\core\classes\ConsulterRetriever.bwc
pause