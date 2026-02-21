@echo off
echo Adding Node firewall rules...
netsh advfirewall firewall add rule name="Node TCP In" dir=in action=allow protocol=TCP localport=7878 profile=any
netsh advfirewall firewall add rule name="Node TCP Out" dir=out action=allow protocol=TCP localport=7878 profile=any
netsh advfirewall firewall add rule name="Node UDP In" dir=in action=allow protocol=UDP localport=5000 profile=any
netsh advfirewall firewall add rule name="Node UDP Out" dir=out action=allow protocol=UDP localport=5000 profile=any
echo.
echo Firewall rules added! Press any key to close...
pause
