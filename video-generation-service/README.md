Project: video-generation-service

Problem and recommendation
--------------------------
On Windows, pip attempted to build Pillow from source and failed with a missing zlib headers/libs error. Pillow 10.4.0 does not provide prebuilt Windows wheels for Python 3.14, so building requires C build tools and development zlib. The recommended and simplest fix is to use Python 3.11 for this project on Windows or use conda to get prebuilt binary packages.

Quick recommended steps (Python 3.11, Windows cmd)
--------------------------------------------------
1. Install Python 3.11 from https://www.python.org/downloads/windows/ (choose the Windows installer).
2. From the project directory, create and activate a venv using Python 3.11:

```cmd
py -3.11 -m venv venv
venv\Scripts\activate
python -m pip install --upgrade pip setuptools wheel
pip install -r requirements.txt
```

3. Verify Pillow installed successfully:

```cmd
python - <<EOF
from PIL import Image
print('Pillow version', Image.__version__)
EOF
```

Alternative: Use conda (recommended if you prefer prebuilt binary stacks)
--------------------------------------------------------------------------
1. Install Miniconda or Anaconda.
2. Create and activate an environment and install binary packages from conda-forge:

```cmd
conda create -n vidsvc python=3.11
conda activate vidsvc
conda install -c conda-forge pillow moviepy numpy imageio imageio-ffmpeg
pip install fastapi uvicorn google-generativeai gtts python-dotenv
```

Advanced alternative: Build Pillow from source on Python 3.14
-------------------------------------------------------------
This is more involved and fragile. You need Microsoft Visual C++ Build Tools and zlib development files available to the compiler (for example via vcpkg). See Pillow install docs for details: https://pillow.readthedocs.io/en/latest/installation.html

If you want, I can:
- Walk you through installing Python 3.11 and recreating the venv here.
- Or walk you through setting up conda and installing the dependencies.
- Or try to set up build tools and zlib and attempt to build Pillow on Python 3.14 (not recommended).

Contact
-------
If you want me to run any of the install steps now, tell me which option to run and I'll provide exact commands or run them in a terminal if you want.

Bootstrap & run (quick reference)
--------------------------------
From the project root, choose either cmd or PowerShell.

Cmd (recommended):

```cmd
scripts\bootstrap.bat
venv\Scripts\activate
python scripts\smoke_test.py
scripts\run_server.bat
```

PowerShell:

```powershell
.\scripts\bootstrap.ps1
.\venv\Scripts\Activate.ps1
python .\scripts\smoke_test.py
.\scripts\run_server.ps1
```

Notes:
- The server scripts set a placeholder `GEMINI_API_KEY` when one is not present so you can start the server without calling Gemini. To test full Gemini integration set `GEMINI_API_KEY` in a `.env` file in the project root before running.
- The smoke test creates a small MP4 under `tmp_videos/smoke_test_output.mp4`.