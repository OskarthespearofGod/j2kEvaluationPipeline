@echo off
where gradle >nul 2>nul
if errorlevel 1 (
  echo gradle is required to run this wrapper shim.
  exit /b 1
)
gradle %*
