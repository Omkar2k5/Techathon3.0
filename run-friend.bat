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
echo ===== NeuroMesh for Friends (Administrator Mode) =====
echo Starting NeuroMesh with elevated privileges...
echo You will connect to your friend's LLM automatically!
echo.

:: Temporarily disable Windows Defender Firewall for testing
echo Temporarily disabling Windows Firewall for testing...
netsh advfirewall set allprofiles state off

:: Add firewall rules for the application
echo Adding firewall rules...
netsh advfirewall firewall add rule name="NeuroMesh TCP" dir=out action=allow protocol=TCP localport=any remoteport=7878
netsh advfirewall firewall add rule name="NeuroMesh TCP In" dir=in action=allow protocol=TCP localport=7878
netsh advfirewall firewall add rule name="NeuroMesh UDP" dir=out action=allow protocol=UDP localport=any remoteport=5000
netsh advfirewall firewall add rule name="NeuroMesh UDP In" dir=in action=allow protocol=UDP localport=5000

:: Re-enable firewall
echo Re-enabling Windows Firewall with rules...
netsh advfirewall set allprofiles state on

echo.
echo Starting NeuroMesh...
neuromesh.exe

pause
