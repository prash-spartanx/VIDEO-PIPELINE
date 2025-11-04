import os
from dotenv import load_dotenv

# Load environment variables from .env file
load_dotenv()

# --- API Keys ---
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
if not GEMINI_API_KEY:
    raise ValueError("CRITICAL: GEMINI_API_KEY is not set in the .env file.")

UNSPLASH_CLIENT_ID = os.getenv("UNSPLASH_CLIENT_ID")
if not UNSPLASH_CLIENT_ID:
    print("WARNING: UNSPLASH_CLIENT_ID is not set. Image downloads will fail.")
UNSPLASH_API = "https://api.unsplash.com/search/photos"

# --- Model Configuration ---
# --- FIX: Set this to the ONLY model name your test.py proved to work ---
MODEL_NAME = "gemini-2.0-flash"

# --- Directory and Path Configuration ---
PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))
VIDEOS_DIR = os.path.join(PROJECT_DIR, "generated_videos")
ASSETS_DIR = os.path.join(PROJECT_DIR, "assets")
BG_MUSIC = os.path.join(ASSETS_DIR, "background_music.mp3") # Optional: place a music file here

# Ensure directories exist on startup
os.makedirs(VIDEOS_DIR, exist_ok=True)
os.makedirs(ASSETS_DIR, exist_ok=True)

# --- Language Configuration ---
LANGUAGE_MAP = {
    "english": "en", "hindi": "hi", "spanish": "es", "french": "fr",
    "german": "de", "italian": "it", "portuguese": "pt", "russian": "ru",
    "japanese": "ja", "korean": "ko", "chinese": "zh-cn", "arabic": "ar"
}

print(f"✅ Config loaded. Videos will be saved to: {VIDEOS_DIR}")
