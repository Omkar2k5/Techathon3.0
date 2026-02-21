@echo off
echo Adding Ollama firewall rules...
netsh advfirewall firewall add rule name="Ollama HTTP In" dir=in action=allow protocol=TCP localport=11434 profile=any
netsh advfirewall firewall add rule name="Ollama HTTP Out" dir=out action=allow protocol=TCP localport=11434 profile=any
echo.
echo Firewall rules added! Press any key to close...
pause
