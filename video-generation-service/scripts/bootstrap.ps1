Write-Host "Bootstrapping project (PowerShell)..."
py -3.11 -m venv venv
& .\venv\Scripts\Activate.ps1
python -m pip install --upgrade pip setuptools wheel
pip install -r requirements.txt
Write-Host "Bootstrap complete."
