@echo off
where mvn >nul 2>&1 || (echo Maven 3.9+ is required. Run scripts\setup-windows.ps1 for checks. & exit /b 1)
mvn %*
