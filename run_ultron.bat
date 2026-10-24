@echo off
title Ultron AI Workstation
cd /d "%~dp0"
echo Starting Ultron AI Desktop Assistant and Mobile Companion Service...
.venv\Scripts\python.exe main.py
pause
