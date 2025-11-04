@echo off
REM Run the FastAPI server using the project's venv (cmd)
set GEMINI_API_KEY=%GEMINI_API_KEY%
if "%GEMINI_API_KEY%"=="" (
  echo WARNING: GEMINI_API_KEY not set; setting placeholder to allow server startup.
  set GEMINI_API_KEY=placeholder
)
call venv\Scripts\activate
echo Starting server on http://127.0.0.1:8000
python -m uvicorn main:app --host 127.0.0.1 --port 8000
