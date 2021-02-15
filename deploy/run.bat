echo off
setlocal

For /F "eol=# tokens=1,* delims==" %%A IN (data.properties) DO (
	set %%A=%%B
)

echo Starting Jenkins loaded with accelQ Plugin on Port 8954
cd /d "%project_location%"
call mvn hpi:run -Djetty.port=%jenkins_server_port%

endlocal
pause > nul
exit