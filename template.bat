@echo off

:: get orginal directory
set now_dir=%cd%
:: get work directory
set work_dir=%~dp0

:: goto work directory
cd /d "%work_dir%"

call gradlew.bat createhelper:assemble

set transformer_jar=%work_dir%\createhelper\build\libs\ratel-create-helper-1.0.jar

java -jar %transformer_jar%  %*

cd /d "%now_dir%"

exit /b 0