@echo off
setlocal
cd /d "%~dp0"

echo ========================================
echo        ScorePlus - Compilacao
echo ========================================
echo.

where java >nul 2>nul
if errorlevel 1 (
    echo ERRO: Java nao encontrado no PATH.
    echo Instale/configure o Java 26 antes de compilar.
    pause
    exit /b 1
)

java -version
if errorlevel 1 (
    echo.
    echo ERRO: nao foi possivel executar o Java.
    pause
    exit /b 1
)

echo.
echo [1/3] Limpando compilacao anterior...
call mvn clean
if errorlevel 1 (
    echo.
    echo ERRO: Falha no mvn clean.
    pause
    exit /b 1
)

echo.
echo [2/3] Compilando ScorePlus...
call mvn package -B
if errorlevel 1 (
    echo.
    echo ERRO: Falha na compilacao.
    pause
    exit /b 1
)

echo.
echo [3/3] Verificando o JAR...
if not exist "target\ScorePlus.jar" (
    echo.
    echo ERRO: target\ScorePlus.jar nao foi gerado.
    pause
    exit /b 1
)

copy /Y "target\ScorePlus.jar" "ScorePlus.jar" >nul
if errorlevel 1 (
    echo.
    echo ERRO: Nao foi possivel copiar o JAR para a pasta do projeto.
    pause
    exit /b 1
)

echo.
echo ========================================
echo        COMPILACAO CONCLUIDA
 echo ========================================
echo JAR: %CD%\ScorePlus.jar
echo.
pause
endlocal
