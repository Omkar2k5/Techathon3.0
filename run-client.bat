@echo off
:: Check for admin rights
net session >nul 2>&1
if %errorLevel% == 0 (
    echo Running with administrator privileges...
    goto :main
) else (
    echo Requesting administrator privileges...
    powershell -Command "Start-Process cmd -ArgumentList '/c %~s0' -Verb RunAs"
    exit /b
)

:main
cd /d "%~dp0"
echo ===== Node Client (Administrator Mode) =====
echo Starting Node Client with elevated privileges...
echo You will connect to the local network seamlessly.
echo.

:: Temporarily disable Windows Defender Firewall for testing
echo Temporarily disabling Windows Firewall for testing...
netsh advfirewall set allprofiles state off

:: Add firewall rules for the application
echo Adding firewall rules...
netsh advfirewall firewall add rule name="Node TCP" dir=out action=allow protocol=TCP localport=any remoteport=7878
netsh advfirewall firewall add rule name="Node TCP In" dir=in action=allow protocol=TCP localport=7878
netsh advfirewall firewall add rule name="Node UDP" dir=out action=allow protocol=UDP localport=any remoteport=5000
netsh advfirewall firewall add rule name="Node UDP In" dir=in action=allow protocol=UDP localport=5000

:: Re-enable firewall
echo Re-enabling Windows Firewall with rules...
netsh advfirewall set allprofiles state on

echo.
echo Starting Node...
neuromesh.exe

pause
