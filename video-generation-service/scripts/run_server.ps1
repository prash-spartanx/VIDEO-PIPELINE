if (-not $env:GEMINI_API_KEY) {
    Write-Warning "GEMINI_API_KEY is not set; using placeholder to allow server startup."
    $env:GEMINI_API_KEY = 'placeholder'
}
& .\\venv\\Scripts\\Activate.ps1
Write-Host "Starting server on http://127.0.0.1:8000"
python -m uvicorn main:app --host 127.0.0.1 --port 8000
