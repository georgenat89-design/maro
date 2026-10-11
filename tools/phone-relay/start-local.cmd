@echo off
cd /d "%~dp0"
py -3 relay.py --port 8765 --database phone-relay.sqlite3
pause
