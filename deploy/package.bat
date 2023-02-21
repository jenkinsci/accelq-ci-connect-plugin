echo off
setlocal

For /F "eol=# tokens=1,* delims==" %%A IN (data.properties) DO (
	set %%A=%%B
)

set temp=%project_location%\core\target\core-1.0-SNAPSHOT.jar

echo Packaging repo
cd /d "%project_location%"
call mvn install:install-file -Dfile="%temp%" -DgroupId=core -DartifactId=aqCore -Dversion=1.0 -Dpackaging=jar