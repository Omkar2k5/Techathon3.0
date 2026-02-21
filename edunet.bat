@echo off
echo ===== EduNet Startup =====
echo.

echo 1. Checking and configuring firewall (one-time setup)...
netsh advfirewall firewall show rule name="Node TCP" >nul 2>&1
if %errorlevel% neq 0 (
    echo Adding firewall rules...
    netsh advfirewall firewall add rule name="Node TCP" dir=in action=allow protocol=TCP localport=7878 profile=any >nul 2>&1
    netsh advfirewall firewall add rule name="Node UDP" dir=in action=allow protocol=UDP localport=5000 profile=any >nul 2>&1
    echo Firewall configured!
) else (
    echo Firewall already configured.
)

echo.
echo 2. Stopping existing processes...
taskkill /f /im edunet.exe 2>nul

echo.
echo 3. Waiting for processes to stop...
timeout /t 3 /nobreak >nul

:start_node
echo.
echo 4. Starting Node...
echo Open http://localhost:3000 in your browser
echo.
.\target\release\edunet.exe

pause