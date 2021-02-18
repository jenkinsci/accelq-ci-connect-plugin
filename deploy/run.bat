echo off
setlocal

For /F "eol=# tokens=1,* delims==" %%A IN (data.properties) DO (
	set %%A=%%B
)

set temp=%project_location%\src\lib\core-1.0-SNAPSHOT.jar

echo Starting Jenkins loaded with accelQ Plugin on Port 8954
cd /d "%project_location%"
call mvn install:install-file -Dfile="%temp%" -DgroupId=core -DartifactId=aqCore -Dversion=1.0 -Dpackaging=jar
call mvn hpi:run -Djetty.port=%jenkins_server_port%

endlocal
pause > nul
exit