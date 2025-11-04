@echo off
REM Bootstrap the project (Windows cmd). Creates a Python 3.11 venv and installs requirements.
echo Bootstrapping project...
py -3.11 -m venv venv
call venv\Scripts\activate
python -m pip install --upgrade pip setuptools wheel
pip install -r requirements.txt
echo Bootstrap complete.
