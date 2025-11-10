import os
import uuid
import requests
import hashlib
import json
import re
import tempfile
import numpy as np
import traceback
from io import BytesIO

from fastapi import FastAPI, HTTPException, BackgroundTasks
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

# NEW: dotenv loading – supports both .env and my.env
try:
    from dotenv import load_dotenv
    # Try default .env
    load_dotenv()
    # If GEMINI_API_KEY still missing, also try my.env (your file name)
    if not os.getenv("GEMINI_API_KEY") and os.path.exists("my.env"):
        load_dotenv("my.env")
except Exception:
    # If python-dotenv somehow not available, continue silently (you already have it in requirements)
    pass

import google.generativeai as genai
from gtts import gTTS
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance

# --------------------
# Environment / config
# --------------------
def _mask(v: str | None) -> str:
    if not v:
        return "None"
    if len(v) <= 6:
        return "***"
    return v[:4] + "…" + v[-2:]

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
MODEL_NAME = os.getenv("MODEL_NAME", "gemini-2.0-flash")
VIDEOS_DIR = os.getenv("VIDEOS_DIR", "/app/videos")
ASSETS_DIR = os.getenv("ASSETS_DIR", "/app/assets")
APP_BASE_URL = os.getenv("APP_BASE_URL", "http://localhost:8000")

UNSPLASH_CLIENT_ID = os.getenv("UNSPLASH_CLIENT_ID", "")
UNSPLASH_API = "https://api.unsplash.com/search/photos"

# Updated LANGUAGE_MAP with 13 regional languages
LANGUAGE_MAP = {
    "english": "en",
    "hindi": "hi",
    "bengali": "bn",
    "telugu": "te",
    "marathi": "mr",
    "tamil": "ta",
    "gujarati": "gu",
    "kannada": "kn",
    "malayalam": "ml",
    "punjabi": "pa",
    "odia": "or",
    "assamese": "as",
    "urdu": "ur",
    "en": "en",
    "hi": "hi",
    "bn": "bn",
    "te": "te",
    "mr": "mr",
    "ta": "ta",
    "gu": "gu",
    "kn": "kn",
    "ml": "ml",
    "pa": "pa",
    "or": "or",
    "as": "as",
    "ur": "ur"
}

BG_MUSIC = os.getenv("BG_MUSIC", "")
DEFAULT_BG_MUSIC_URL = "https://cdn.pixabay.com/download/audio/2022/03/10/audio_2c4d768e91.mp3"
DOWNLOADED_BG_MUSIC = os.path.join(ASSETS_DIR, "default_bg_music.mp3")

# Quick diagnostics (masked)
print(f"🔧 ENV check → MODEL_NAME={MODEL_NAME}, GEMINI_API_KEY={_mask(GEMINI_API_KEY)}")
if not GEMINI_API_KEY:
    print("⚠️ GEMINI_API_KEY not found in environment. "
          "Make sure it is in `.env` or `my.env`, or pass -e GEMINI_API_KEY=...")

# -------------
# FastAPI app
# -------------
app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080", "http://127.0.0.1:8080", "http://localhost:3000"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
    expose_headers=["Content-Type", "Content-Disposition"],
)

# Pillow antialias patch
from PIL import Image  # re-import to ensure symbol exists
if not hasattr(Image, 'ANTIALIAS'):
    Image.ANTIALIAS = Image.LANCZOS

# MoviePy import
MOVIEPY_AVAILABLE = False
try:
    from moviepy.editor import (
        ImageClip, VideoClip, concatenate_videoclips,
        AudioFileClip, CompositeAudioClip, concatenate_audioclips
    )
    MOVIEPY_AVAILABLE = True
    print("✅ MoviePy successfully imported")
except Exception as e:
    print(f"❌ MoviePy import failed: {e}")

os.makedirs(VIDEOS_DIR, exist_ok=True)
os.makedirs(ASSETS_DIR, exist_ok=True)
app.mount("/videos", StaticFiles(directory=VIDEOS_DIR), name="videos")

# -------------
# DTOs
# -------------
class VideoGenerationRequest(BaseModel):
    content: str
    language: str = "english"
    script_override: str | None = None
    title: str | None = None

class ImproviseRequest(BaseModel):
    content: str
    language: str = "english"
    style_hints: str | None = None

class ImproviseResponse(BaseModel):
    improved_script: str

class JobStatusResponse(BaseModel):
    job_id: str
    status: str
    message: str
    video_url: str | None = None

class JobCreationResponse(BaseModel):
    job_id: str
    message: str

# -------------
# Model init
# -------------
GENERATIVE_MODEL = None

def get_valid_model():
    global GENERATIVE_MODEL
    if GENERATIVE_MODEL:
        return GENERATIVE_MODEL
    if not GEMINI_API_KEY:
        raise RuntimeError("GEMINI_API_KEY is not set")
    genai.configure(api_key=GEMINI_API_KEY)
    try:
        GENERATIVE_MODEL = genai.GenerativeModel(MODEL_NAME)
        print(f"✅ Using model: {MODEL_NAME}")
        return GENERATIVE_MODEL
    except Exception as e:
        raise RuntimeError(f"Failed to init model {MODEL_NAME}: {e}")

# -------------------
# Helpers / safeguards
# -------------------
def download_default_bg_music():
    if os.path.exists(DOWNLOADED_BG_MUSIC):
        return
    try:
        r = requests.get(DEFAULT_BG_MUSIC_URL, timeout=30)
        r.raise_for_status()
        with open(DOWNLOADED_BG_MUSIC, "wb") as f:
            f.write(r.content)
        print("✅ Downloaded default BG music")
    except Exception as e:
        print(f"⚠️ Could not download default BG music: {e}")

BAD_SNIPPETS = [
    "javascript must be enabled",
    "press information bureau (pib) is the nodal agency",
    "enable javascript",
    "browser does not support",
]

def content_is_bad(text: str) -> bool:
    if not text:
        return True
    t = re.sub(r"\s+", " ", text).strip()
    if len(t) < 200:
        return True
    tl = t.lower()
    return any(s in tl for s in BAD_SNIPPETS)

def create_placeholder_image(path: str):
    img = Image.new('RGB', (1920, 1080), color='#141926')
    d = ImageDraw.Draw(img)
    for y in range(1080):
        shade = 22 + int((y / 1080) * 38)
        d.line([(0, y), (1920, y)], fill=(shade, shade, shade + 10))
    vignette = Image.new('L', (1920, 1080), 0)
    dv = ImageDraw.Draw(vignette)
    dv.ellipse((-480, -270, 2400, 1350), fill=180)
    vignette = vignette.filter(ImageFilter.GaussianBlur(120))
    img = Image.composite(img, Image.new('RGB', (1920, 1080), '#0f1320'), vignette)
    img = ImageEnhance.Contrast(img).enhance(1.06)
    img = ImageEnhance.Sharpness(img).enhance(1.03)
    img.save(path, "JPEG", quality=90)

def _fallback_query_generation(content: str, title: str | None) -> list[str]:
    base = f"{title or ''} {content}".lower()
    cleaned = re.sub(r'[^\w\s]', ' ', base)
    words = cleaned.split()
    stop = {'the','is','at','which','on','a','an','as','are','was','were','been','be',
            'have','has','had','do','does','did','will','would','ministry','department',
            'government','announced','said','stated','press','release','pib'}
    keywords = [w for w in words if len(w) > 4 and w not in stop][:14]
    q = []
    for i in range(0, min(len(keywords), 6), 2):
        if i+1 < len(keywords):
            q.append(f"{keywords[i]} {keywords[i+1]}")
        else:
            q.append(keywords[i])
    while len(q) < 7:
        q.append("india business forum")
    return list(dict.fromkeys(q))[:8]

def _generate_image_queries(content: str, title: str | None, model) -> list[str]:
    try:
        prompt = (
            "Generate 7-8 specific stock-photo search queries (landscape) that match this news item. "
            "Return ONLY a JSON array of strings.\n\n"
            f"TITLE: {title or 'N/A'}\n"
            f"TEXT: {content[:800]}"
        )
        resp = model.generate_content(prompt)
        m = re.search(r'\[.*\]', resp.text, re.DOTALL)
        if m:
            arr = json.loads(m.group())
            if isinstance(arr, list) and all(isinstance(x, str) for x in arr):
                cleaned = [x.strip() for x in arr if x.strip()]
                if len(cleaned) >= 5:
                    return cleaned[:8]
    except Exception:
        pass
    return _fallback_query_generation(content, title)

def download_assets(content: str, title: str | None) -> list[str]:
    os.makedirs(ASSETS_DIR, exist_ok=True)
    model = get_valid_model()
    queries = _generate_image_queries(content, title, model)
    headers = {"Authorization": f"Client-ID {UNSPLASH_CLIENT_ID}"} if UNSPLASH_CLIENT_ID else {}

    files = []
    for i, q in enumerate(queries, 1):
        try:
            if not UNSPLASH_CLIENT_ID:
                break
            r = requests.get(
                f"{UNSPLASH_API}?query={requests.utils.quote(q)}&orientation=landscape&per_page=1",
                headers=headers, timeout=20
            )
            r.raise_for_status()
            data = r.json()
            if data.get("results"):
                img_url = data["results"][0]["urls"]["regular"]
                ir = requests.get(img_url, timeout=20)
                ir.raise_for_status()
                h = hashlib.sha256(ir.content).hexdigest()[:10]
                fp = os.path.join(ASSETS_DIR, f"bg_{h}_{i}.jpg")
                img = Image.open(BytesIO(ir.content)).resize((1920, 1080), resample=Image.LANCZOS)
                img = ImageEnhance.Contrast(img).enhance(1.08)
                img = ImageEnhance.Sharpness(img).enhance(1.05)
                img.save(fp, "JPEG", quality=92)
                files.append(fp)
        except Exception as e:
            print(f"⚠️ Image fetch failed for '{q}': {e}")

    while len(files) < 7:
        ph = os.path.join(ASSETS_DIR, f"placeholder_{len(files)+1}.jpg")
        create_placeholder_image(ph)
        files.append(ph)

    return files

def apply_smooth_zoom(clip, z='in', k=0.12):
    w, h = clip.size
    duration = clip.duration

    if z == 'in':
        return clip.resize(lambda t: 1 + k * (t / duration))
    if z == 'out':
        return clip.resize(lambda t: 1 + k * (1 - t / duration))

    def pan(right: bool):
        def make_frame(t):
            import numpy as _np
            from PIL import Image as _Image
            progress = t / duration
            zf = 1 + k * 0.5
            frame = clip.get_frame(t)
            zoomed = _np.array(_Image.fromarray(frame).resize((int(w*zf), int(h*zf)), _Image.LANCZOS))
            x_off = int((zoomed.shape[1] - w) * ((1 - progress) if right else progress))
            return zoomed[:h, x_off:x_off+w]
        return VideoClip(make_frame, duration=duration)

    if z == 'left':
        return pan(False)
    if z == 'right':
        return pan(True)
    return clip

def create_video_file(script: str, output_path: str, language: str, image_paths: list[str]):
    if not MOVIEPY_AVAILABLE:
        raise RuntimeError("MoviePy not available")

    sentences = [s.strip() + '.' for s in script.replace("\n", " ").split(".") if s.strip()]
    if not sentences:
        raise ValueError("Script contains no valid sentences")

    tmp = tempfile.gettempdir()
    audio_clips = []
    for i, s in enumerate(sentences):
        ap = os.path.join(tmp, f"tts_{uuid.uuid4().hex}.mp3")
        try:
            gTTS(text=s, lang=language, slow=False).save(ap)
            audio_clips.append(AudioFileClip(ap))
        except Exception as e:
            print(f"⚠️ gTTS failed for sentence {i}: {e}")

    if not audio_clips:
        raise RuntimeError("No audio generated")

    total_audio = sum(c.duration for c in audio_clips)

    # ensure enough images
    while len(image_paths) < len(sentences):
        image_paths.extend(image_paths[:len(sentences) - len(image_paths)])

    fade = 0.8
    overlap = 0.45
    dur = (total_audio + (len(image_paths) - 1) * overlap) / len(image_paths)

    zooms = ['in', 'out', 'left', 'right']
    clips = []
    for i, path in enumerate(image_paths):
        base = ImageClip(path, duration=dur)
        eff = apply_smooth_zoom(base, z=zooms[i % len(zooms)], k=0.12)
        if i == 0:
            eff = eff.fadein(fade)
        elif i == len(image_paths) - 1:
            eff = eff.fadeout(fade)
        else:
            eff = eff.fadein(fade).fadeout(fade)
        clips.append(eff)

    for i in range(1, len(clips)):
        clips[i] = clips[i].set_start(clips[i-1].start + dur - overlap)
    video = concatenate_videoclips(clips, method="compose", padding=-overlap)

    narration = concatenate_audioclips(audio_clips)

    final_audio = narration
    music_path = BG_MUSIC if (BG_MUSIC and os.path.exists(BG_MUSIC)) else DOWNLOADED_BG_MUSIC
    if os.path.exists(music_path):
        try:
            music = AudioFileClip(music_path).volumex(0.08)
            if music.duration < total_audio:
                loops = int(total_audio / music.duration) + 1
                music = concatenate_audioclips([music] * loops)
            music = music.subclip(0, total_audio).audio_fadein(1.5).audio_fadeout(1.5)
            final_audio = CompositeAudioClip([narration, music])
        except Exception as e:
            print(f"⚠️ BG music mix failed: {e}")

    video = video.set_audio(final_audio).set_duration(total_audio)
    video.write_videofile(
        output_path, fps=30, codec="libx264", audio_codec="aac",
        bitrate="5000k", audio_bitrate="192k", threads=4, preset="medium",
        logger=None
    )

    # cleanup temp audio
    for c in audio_clips:
        try:
            p = c.filename  # type: ignore
            c.close()
            if p and os.path.exists(p):
                os.remove(p)
        except Exception:
            pass

# ----------
# Job state
# ----------
video_jobs: dict[str, dict] = {}

def create_video_task(job_id: str, content: str, language_ui: str,
                      script_override: str | None = None, title: str | None = None):
    if content_is_bad(content):
        video_jobs[job_id] = {
            "status": "failed",
            "message": "Press release content is empty/blocked (e.g., 'Enable JavaScript'). Please re-scrape or create it manually.",
            "video_url": None
        }
        return

    video_jobs[job_id] = {"status": "processing", "message": "Generating...", "video_url": None}
    try:
        model = get_valid_model()
        lang_code = LANGUAGE_MAP.get(language_ui.lower(), "en")

        if script_override and script_override.strip():
            script = script_override.strip()
        else:
            prompt = (
                "Rewrite the following press-release text as a concise, engaging narration for a news/documentary "
                f"voiceover in {language_ui}. Keep it factual, 30–45 seconds spoken. Output only the narration text.\n\n"
                f"TEXT:\n{content}"
            )
            script = model.generate_content(prompt).text.strip().replace("*", "").replace("#", "")

        images = download_assets(content, title)
        filename = f"video_{job_id[:12]}.mp4"
        out_path = os.path.join(VIDEOS_DIR, filename)

        create_video_file(script, out_path, lang_code, images)

        video_jobs[job_id].update({
            "status": "completed",
            "video_url": f"{APP_BASE_URL}/videos/{filename}",
            "message": "Video generation completed successfully."
        })
    except Exception as e:
        video_jobs[job_id].update({
            "status": "failed",
            "message": f"Video generation failed: {e}"
        })
        traceback.print_exc()

# -----------
# Endpoints
# -----------
@app.on_event("startup")
def on_startup():
    os.makedirs(VIDEOS_DIR, exist_ok=True)
    os.makedirs(ASSETS_DIR, exist_ok=True)
    download_default_bg_music()
    print(f"✅ Videos dir: {VIDEOS_DIR}")
    print(f"✅ Assets dir: {ASSETS_DIR}")
    print(f"✅ Public base: {APP_BASE_URL}")

@app.get("/")
def root():
    return {"message": "Video Generation API running (no on-video text, content validation enabled)"}

@app.post("/generate-video", response_model=JobCreationResponse)
def generate_video_endpoint(req: VideoGenerationRequest, background_tasks: BackgroundTasks):
    job_id = uuid.uuid4().hex
    video_jobs[job_id] = {"status": "pending", "message": "Job queued", "video_url": None}
    background_tasks.add_task(
        create_video_task, job_id, req.content, req.language, req.script_override, req.title
    )
    return {"job_id": job_id, "message": "Video generation job has been started."}

@app.get("/video-status/{job_id}", response_model=JobStatusResponse)
def get_video_status(job_id: str):
    job = video_jobs.get(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Job ID not found.")
    return JobStatusResponse(job_id=job_id, **job)

@app.post("/improvise", response_model=ImproviseResponse)
def improvise_endpoint(req: ImproviseRequest):
    try:
        model = get_valid_model()
        hints = (req.style_hints or "").strip()
        prompt = (
            "Improve this narration for a professional voiceover. "
            f"Language: {req.language}. Keep facts intact, 30–45 seconds spoken. "
            f"Style hints: {hints or 'None'}. Output only narration text.\n\n"
            f"TEXT:\n{req.content}"
        )
        out = model.generate_content(prompt).text.strip().replace("*", "").replace("#", "")
        if not out:
            raise RuntimeError("Empty model response")
        return {"improved_script": out}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Improvise failed: {e}")

@app.post("/admin/clear-assets")
def clear_assets():
    removed = 0
    for name in os.listdir(ASSETS_DIR):
        if name.startswith(("placeholder_", "bg_")):
            try:
                os.remove(os.path.join(ASSETS_DIR, name))
                removed += 1
            except Exception:
                pass
    return {"removed": removed}