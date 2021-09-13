@echo off

:: get orginal directory
set now_dir=%cd%
:: get work directory
set work_dir=%~dp0

:: goto work directory
cd /d "%work_dir%"


set helper_jar=%work_dir%\script\ratel-create-helper-1.0.jar

java -jar %helper_jar%  %*

cd /d "%now_dir%"

exit /b 0