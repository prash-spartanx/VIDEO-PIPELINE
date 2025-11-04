import os
import uuid
import requests
import hashlib
import json
import re
import tempfile
import numpy as np
import time
import traceback
from datetime import datetime, timedelta
from io import BytesIO
from fastapi import FastAPI, HTTPException, BackgroundTasks
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
import google.generativeai as genai
from config import GEMINI_API_KEY, VIDEOS_DIR, LANGUAGE_MAP, ASSETS_DIR, BG_MUSIC, UNSPLASH_CLIENT_ID, UNSPLASH_API, MODEL_NAME
from gtts import gTTS
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance

app = FastAPI()

from fastapi.middleware.cors import CORSMiddleware

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080", "http://localhost:3000", "http://127.0.0.1:8080"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
    expose_headers=["Content-Type", "Content-Disposition"]
)

# --- MONKEY PATCH FOR MOVIEPY ANTIALIAS ISSUE ---
if not hasattr(Image, 'ANTIALIAS'):
    Image.ANTIALIAS = Image.LANCZOS

# --- FIXED MOVIEPY IMPORTS ---
MOVIEPY_AVAILABLE = False
try:
    from moviepy.editor import (
        ImageClip, 
        VideoClip,
        concatenate_videoclips, 
        AudioFileClip, 
        CompositeAudioClip, 
        concatenate_audioclips
    )
    # Import only available fx - crossfadein/crossfadeout are deprecated
    from moviepy.video.fx.all import resize, fadein, fadeout
    
    MOVIEPY_AVAILABLE = True
    print("✅ MoviePy successfully imported and available")
except Exception as e:
    print(f"❌ CRITICAL: MoviePy import failed: {e}")
    MOVIEPY_AVAILABLE = False

app.mount("/videos", StaticFiles(directory=VIDEOS_DIR), name="videos")

# --- This reads the URL from docker-compose.yml ---
APP_BASE_URL = os.getenv("APP_BASE_URL", "http://localhost:8000")

# Default background music URL (royalty-free)
DEFAULT_BG_MUSIC_URL = "https://cdn.pixabay.com/download/audio/2022/03/10/audio_2c4d768e91.mp3"
DOWNLOADED_BG_MUSIC = os.path.join(ASSETS_DIR, "default_bg_music.mp3")

video_jobs = {}

class VideoGenerationRequest(BaseModel):
    content: str
    language: str = "en"

class JobStatusResponse(BaseModel):
    job_id: str
    status: str
    message: str
    video_url: str | None = None

class JobCreationResponse(BaseModel):
    job_id: str
    message: str

GENERATIVE_MODEL = None

@app.on_event("startup")
def on_startup():
    os.makedirs(VIDEOS_DIR, exist_ok=True)
    os.makedirs(ASSETS_DIR, exist_ok=True)
    print(f"✅ Config loaded. Videos will be saved to: {VIDEOS_DIR}")
    print(f"✅ Public-facing URL set to: {APP_BASE_URL}")
    print(f"✅ MoviePy Available: {MOVIEPY_AVAILABLE}")
    download_default_bg_music()
    get_valid_model()

def download_default_bg_music():
    """Download default background music if not present"""
    if os.path.exists(DOWNLOADED_BG_MUSIC):
        print(f"✅ Background music already exists at: {DOWNLOADED_BG_MUSIC}")
        return
    
    try:
        print(f"⬇️ Downloading default background music...")
        response = requests.get(DEFAULT_BG_MUSIC_URL, timeout=30)
        response.raise_for_status()
        
        with open(DOWNLOADED_BG_MUSIC, 'wb') as f:
            f.write(response.content)
        print(f"✅ Background music downloaded successfully to: {DOWNLOADED_BG_MUSIC}")
    except Exception as e:
        print(f"⚠️ Could not download background music: {e}")

def get_valid_model():
    """Lazily initializes and returns a single, shared generative model instance."""
    global GENERATIVE_MODEL
    if GENERATIVE_MODEL:
        return GENERATIVE_MODEL

    print("🧠 Initializing Generative AI Model for the first time...")
    try:
        genai.configure(api_key=GEMINI_API_KEY)
        primary_model_name = MODEL_NAME
        
        try:
            model = genai.GenerativeModel(primary_model_name)
            print(f"✅ Using primary model: {primary_model_name}")
            GENERATIVE_MODEL = model
            return GENERATIVE_MODEL
        except Exception as e:
            print(f"❌ FATAL: Model '{primary_model_name}' failed. Error: {e}")
            raise RuntimeError(f"Could not initialize the generative AI model: {primary_model_name}")
    
    except Exception as e:
        print(f"❌ FATAL: genai.configure() failed. Check API key? Error: {e}")
        raise RuntimeError("Could not configure generative AI models.")

def _generate_image_queries(content: str, model: genai.GenerativeModel) -> list[str]:
    """Uses the generative model to create relevant and highly specific image search queries."""
    try:
        prompt = (
            "Based on the following press release or news content, generate a list of 7-8 HIGHLY SPECIFIC and RELEVANT image search queries "
            "for a stock photo API like Unsplash. The queries should be:\n"
            "- Directly related to the key topics, subjects, or themes in the text\n"
            "- Descriptive and concrete (2-5 words each)\n"
            "- Visually evocative and suitable for news/documentary style videos\n"
            "- Varied to cover different aspects of the story\n"
            "- Professional and appropriate for government/news content\n\n"
            "Return ONLY a valid JSON array of strings. Example: [\"ministry building delhi\", \"agriculture technology\", \"farmers working field\"]\n\n"
            f"TEXT: \"{content[:800]}\""
        )
        
        response = model.generate_content(prompt)
        json_match = re.search(r'\[.*\]', response.text, re.DOTALL)
        
        if json_match:
            queries = json.loads(json_match.group())
            if isinstance(queries, list) and all(isinstance(q, str) for q in queries) and len(queries) >= 5:
                cleaned_queries = [q.strip() for q in queries if q.strip() and len(q.strip()) > 3]
                print(f"✅ AI-generated {len(cleaned_queries)} image queries: {cleaned_queries}")
                return cleaned_queries[:8]
            else:
                print("⚠️ AI returned insufficient queries. Using fallback.")
    except Exception as e:
        print(f"⚠️ AI query generation failed: {e}. Using fallback method.")
    
    return _fallback_query_generation(content)

def _fallback_query_generation(content: str) -> list[str]:
    """Enhanced fallback method to generate relevant search queries"""
    print("🔄 Executing enhanced fallback query generation...")
    
    cleaned = re.sub(r'[^\w\s]', ' ', content.lower())
    words = cleaned.split()
    
    stopwords = {'the', 'is', 'at', 'which', 'on', 'a', 'an', 'as', 'are', 'was', 'were', 
                 'been', 'be', 'have', 'has', 'had', 'do', 'does', 'did', 'will', 'would',
                 'ministry', 'department', 'government', 'announced', 'said', 'stated'}
    
    keywords = [w for w in words if len(w) > 4 and w not in stopwords][:15]
    
    queries = []
    
    topic_keywords = {
        'agriculture': ['farming india', 'agriculture technology', 'rural development'],
        'education': ['indian education', 'school students', 'university campus'],
        'health': ['hospital india', 'medical technology', 'healthcare workers'],
        'technology': ['digital india', 'technology innovation', 'modern infrastructure'],
        'infrastructure': ['construction india', 'urban development', 'modern buildings'],
        'defense': ['indian military', 'defense technology', 'security forces'],
        'finance': ['indian economy', 'financial growth', 'banking system'],
        'environment': ['clean energy india', 'environmental conservation', 'green technology']
    }
    
    for topic, topic_queries in topic_keywords.items():
        if topic in content.lower():
            queries.extend(topic_queries[:2])
    
    for i in range(0, min(len(keywords), 6), 2):
        if i + 1 < len(keywords):
            queries.append(f"{keywords[i]} {keywords[i+1]}")
        else:
            queries.append(keywords[i])
    
    generic_queries = ['india development', 'indian culture', 'modern india', 
                      'indian government', 'india progress', 'indian cities']
    
    while len(queries) < 7:
        queries.append(generic_queries[len(queries) % len(generic_queries)])
    
    final_queries = list(dict.fromkeys(queries))[:8]
    print(f"✅ Generated {len(final_queries)} fallback queries: {final_queries}")
    return final_queries

def download_assets(content: str) -> list[str]:
    """Download 7-8 relevant images using AI-generated queries with retries"""
    os.makedirs(ASSETS_DIR, exist_ok=True)
    
    print("🧠 Generating targeted image queries with AI...")
    model = get_valid_model()
    image_queries = _generate_image_queries(content, model)
    
    if not image_queries:
        raise RuntimeError("Image query generation failed to produce any search terms.")

    headers = {"Authorization": f"Client-ID {UNSPLASH_CLIENT_ID}"} if UNSPLASH_CLIENT_ID else {}
    downloaded_files = []
    
    for i, query in enumerate(image_queries, 1):
        try:
            if not UNSPLASH_CLIENT_ID:
                print("⚠️ No Unsplash client ID, using placeholder.")
                break
                
            print(f"🔎 [{i}/{len(image_queries)}] Fetching image for: '{query}'")
            
            response = requests.get(
                f"{UNSPLASH_API}?query={requests.utils.quote(query)}&orientation=landscape&per_page=1",
                headers=headers,
                timeout=20
            )
            response.raise_for_status()
            data = response.json()
            
            if data.get('results') and len(data['results']) > 0:
                img_url = data['results'][0]['urls']['regular']
                img_resp = requests.get(img_url, timeout=20)
                img_resp.raise_for_status()
                
                file_hash = hashlib.sha256(img_resp.content).hexdigest()[:10]
                target_path = os.path.join(ASSETS_DIR, f"bg_{file_hash}_{i}.jpg")
                
                img = Image.open(BytesIO(img_resp.content))
                img = img.resize((1920, 1080), resample=Image.LANCZOS)
                
                enhancer = ImageEnhance.Contrast(img)
                img = enhancer.enhance(1.1)
                enhancer = ImageEnhance.Sharpness(img)
                img = enhancer.enhance(1.05)
                
                img.save(target_path, "JPEG", quality=95)
                downloaded_files.append(target_path)
                print(f"✅ Downloaded '{query}' -> {os.path.basename(target_path)}")
            else:
                print(f"⚠️ No results for '{query}', trying alternate query...")
                alt_query = query.split()[0] if ' ' in query else query
                response = requests.get(
                    f"{UNSPLASH_API}?query={requests.utils.quote(alt_query)}&orientation=landscape&per_page=1",
                    headers=headers,
                    timeout=20
                )
                data = response.json()
                if data.get('results'):
                    img_url = data['results'][0]['urls']['regular']
                    img_resp = requests.get(img_url, timeout=20)
                    file_hash = hashlib.sha256(img_resp.content).hexdigest()[:10]
                    target_path = os.path.join(ASSETS_DIR, f"bg_{file_hash}_{i}.jpg")
                    img = Image.open(BytesIO(img_resp.content)).resize((1920, 1080), resample=Image.LANCZOS)
                    img.save(target_path, "JPEG", quality=95)
                    downloaded_files.append(target_path)
                    print(f"✅ Used alternate query for image {i}")
                    
        except Exception as e:
            print(f"❌ Failed to download image for '{query}': {e}")
            continue

    if len(downloaded_files) < 5:
        print(f"⚠️ Only downloaded {len(downloaded_files)} images. Creating placeholders...")
        for idx in range(len(downloaded_files), 7):
            placeholder_path = os.path.join(ASSETS_DIR, f"placeholder_{idx}.jpg")
            create_placeholder_image(placeholder_path, f"Image {idx + 1}")
            downloaded_files.append(placeholder_path)
    
    print(f"✅ Total images ready: {len(downloaded_files)}")
    return downloaded_files

def create_placeholder_image(path: str, text: str):
    """Create a visually appealing placeholder image"""
    img = Image.new('RGB', (1920, 1080), color='#1a1a2e')
    d = ImageDraw.Draw(img)
    
    for y in range(1080):
        shade = int(26 + (y / 1080) * 40)
        color = (shade, shade, shade + 20)
        d.line([(0, y), (1920, y)], fill=color)
    
    try:
        d.text((960, 540), text, fill=(255, 255, 255), anchor="mm")
    except:
        d.text((10, 10), text, fill=(255, 255, 255))
    
    img.save(path, "JPEG", quality=85)

def apply_smooth_zoom(clip, zoom_type='in', intensity=0.15):
    """
    Apply smooth zoom effect using lambda functions
    zoom_type: 'in', 'out', 'left', 'right'
    intensity: zoom factor (0.1 to 0.3 recommended)
    """
    w, h = clip.size
    duration = clip.duration
    
    if zoom_type == 'in':
        # Zoom in from 1.0 to 1.0+intensity
        return clip.resize(lambda t: 1 + intensity * (t / duration))
    elif zoom_type == 'out':
        # Zoom out from 1.0+intensity to 1.0
        return clip.resize(lambda t: 1 + intensity * (1 - t / duration))
    elif zoom_type == 'left':
        # Pan left with slight zoom
        def make_frame(t):
            progress = t / duration
            zoom_factor = 1 + intensity * 0.5
            frame = clip.get_frame(t)
            zoomed = np.array(Image.fromarray(frame).resize(
                (int(w * zoom_factor), int(h * zoom_factor)), 
                Image.LANCZOS
            ))
            x_offset = int((zoomed.shape[1] - w) * progress)
            return zoomed[:h, x_offset:x_offset + w]
        return VideoClip(make_frame, duration=duration)
    elif zoom_type == 'right':
        # Pan right with slight zoom
        def make_frame(t):
            progress = t / duration
            zoom_factor = 1 + intensity * 0.5
            frame = clip.get_frame(t)
            zoomed = np.array(Image.fromarray(frame).resize(
                (int(w * zoom_factor), int(h * zoom_factor)), 
                Image.LANCZOS
            ))
            x_offset = int((zoomed.shape[1] - w) * (1 - progress))
            return zoomed[:h, x_offset:x_offset + w]
        return VideoClip(make_frame, duration=duration)
    
    return clip

def create_video_file(script: str, output_path: str, language: str, image_paths: list[str]):
    """Creates industry-level professional video with smooth transitions and effects"""
    print(f"🎬 Starting industry-level video assembly for {output_path}")
    
    if not MOVIEPY_AVAILABLE:
        raise RuntimeError("MoviePy is not available. Cannot create video.")

    sentences = [s.strip() + '.' for s in script.replace("\n", " ").split(".") if s.strip()]
    if not sentences:
        raise ValueError("Script contains no valid sentences.")

    print(f"📝 Processing {len(sentences)} sentences with {len(image_paths)} images")

    # Generate audio clips
    temp_dir = tempfile.gettempdir()
    audio_clips_info = []
    
    for i, sentence in enumerate(sentences):
        audio_path = os.path.join(temp_dir, f"temp_audio_{uuid.uuid4().hex}.mp3")
        try:
            tts = gTTS(text=sentence, lang=language, slow=False)
            tts.save(audio_path)
            audio_clip = AudioFileClip(audio_path)
            audio_clips_info.append({'clip': audio_clip, 'path': audio_path})
            print(f"🔊 Generated audio for sentence {i + 1}/{len(sentences)}")
        except Exception as e:
            print(f"❌ gTTS failed for sentence {i}: {e}. Skipping.")
    
    if not audio_clips_info:
        raise RuntimeError("No audio clips were generated. Aborting video creation.")

    total_audio_duration = sum(info['clip'].duration for info in audio_clips_info)
    
    # Ensure enough images
    while len(image_paths) < len(sentences):
        image_paths.extend(image_paths[:len(sentences) - len(image_paths)])
    
    # Add padding for smooth transitions
    fade_duration = 1.0
    overlap_duration = 0.5
    duration_per_image = (total_audio_duration + (len(image_paths) - 1) * overlap_duration) / len(image_paths)
    
    print(f"⏱️ Total duration: {total_audio_duration:.2f}s | Per image: {duration_per_image:.2f}s")

    # Create video clips with professional effects
    video_clips = []
    zoom_effects = ['in', 'out', 'left', 'right']
    
    for i, img_path in enumerate(image_paths):
        try:
            effect_type = zoom_effects[i % len(zoom_effects)]
            
            # Create base clip
            clip = ImageClip(img_path, duration=duration_per_image)
            
            # Apply smooth zoom/pan effect
            clip = apply_smooth_zoom(clip, zoom_type=effect_type, intensity=0.12)
            
            # Apply professional fade effects
            if i == 0:
                # First clip: fade in only
                clip = clip.fadein(fade_duration)
            elif i == len(image_paths) - 1:
                # Last clip: fade out only
                clip = clip.fadeout(fade_duration)
            else:
                # Middle clips: both fades for smooth transitions
                clip = clip.fadein(fade_duration).fadeout(fade_duration)
            
            video_clips.append(clip)
            print(f"🎥 Processed clip {i + 1}/{len(image_paths)} with '{effect_type}' effect")
            
        except Exception as e:
            print(f"⚠️ Error processing image {i}: {e}")
            traceback.print_exc()
            # Fallback: simple clip
            clip = ImageClip(img_path, duration=duration_per_image)
            if i == 0:
                clip = clip.fadein(0.5)
            elif i == len(image_paths) - 1:
                clip = clip.fadeout(0.5)
            video_clips.append(clip)

    # Concatenate with overlapping transitions
    print("🎞️ Concatenating video clips with smooth transitions...")
    
    # Set start times for overlapping effect
    for i, clip in enumerate(video_clips):
        if i == 0:
            clip = clip.set_start(0)
        else:
            # Start slightly before previous clip ends for smooth crossfade
            clip = clip.set_start(video_clips[i-1].start + duration_per_image - overlap_duration)
    
    # Create final video composition
    final_video = concatenate_videoclips(video_clips, method="compose", padding=-overlap_duration)
    
    # Combine audio
    combined_audio = concatenate_audioclips([info['clip'] for info in audio_clips_info])

    # Add professional background music
    final_audio = combined_audio
    bg_music_path = BG_MUSIC if (BG_MUSIC and os.path.exists(BG_MUSIC)) else DOWNLOADED_BG_MUSIC
    
    if os.path.exists(bg_music_path):
        try:
            print(f"🎵 Adding background music from: {os.path.basename(bg_music_path)}")
            music_clip = AudioFileClip(bg_music_path).volumex(0.08)
            
            if music_clip.duration < total_audio_duration:
                loops_needed = int(total_audio_duration / music_clip.duration) + 1
                music_clip = concatenate_audioclips([music_clip] * loops_needed)
            
            music_clip = music_clip.subclip(0, total_audio_duration)
            music_clip = music_clip.audio_fadein(2.0).audio_fadeout(2.0)
            
            final_audio = CompositeAudioClip([combined_audio, music_clip])
            print("✅ Background music added successfully")
        except Exception as e:
            print(f"⚠️ Could not add background music: {e}")

    final_video = final_video.set_audio(final_audio).set_duration(total_audio_duration)
    
    print("✍️ Writing final industry-level video file...")
    final_video.write_videofile(
        output_path,
        fps=30,
        codec="libx264",
        audio_codec="aac",
        bitrate="5000k",  # High bitrate for broadcast quality
        audio_bitrate="192k",
        threads=4,
        preset='medium',
        logger=None
    )
    
    print(f"✅ Industry-level video successfully created at: {output_path}")

    # Cleanup
    final_video.close()
    for info in audio_clips_info:
        info['clip'].close()
        try:
            os.remove(info['path'])
        except Exception as e:
            print(f"⚠️ Could not delete temp audio file {info['path']}: {e}")

# --- Background Task ---
def create_video_task(job_id: str, content: str, language: str):
    print(f"Job {job_id}: RECEIVED CONTENT:\n---\n{content[:200]}...\n---")
    global video_jobs
    video_jobs[job_id]["status"] = "processing"
    
    try:
        print(f"Job {job_id}: Starting enhanced video generation process.")
        model = get_valid_model()
        language_code = LANGUAGE_MAP.get(language.lower(), "en")

        prompt = (
            "You are a professional scriptwriter for news and documentary videos. "
            "Your task is to transform the following press release or news content into an engaging, "
            "informative narration suitable for a video presentation.\n\n"
            "Requirements:\n"
            "- Create a natural, conversational narration style\n"
            "- Maintain journalistic integrity and factual accuracy\n"
            f"- Write in {language} language\n"
            "- Keep the narration focused and relevant to the topic\n"
            "- Use clear, professional language suitable for news broadcasting\n"
            "- Structure it with a clear beginning, middle, and end\n"
            "- Length: 30-45 seconds when spoken\n\n"
            "Output ONLY the narration text. No stage directions, no labels, no commentary.\n\n"
            f"CONTENT:\n{content}"
        )
        
        script = model.generate_content(prompt).text.strip().replace("*", "").replace("#", "")
        print(f"Job {job_id}: Enhanced script generated ({len(script)} chars)")

        image_paths = download_assets(content)
        
        video_filename = f"video_{job_id[:12]}.mp4"
        output_path = os.path.join(VIDEOS_DIR, video_filename)

        create_video_file(script, output_path, language_code, image_paths)

        video_jobs[job_id].update({
            "status": "completed",
            "video_url": f"{APP_BASE_URL}/videos/{video_filename}",
            "message": "Video generation completed successfully."
        })
        print(f"✅ Job {job_id} completed successfully. URL: {video_jobs[job_id]['video_url']}")

    except Exception as e:
        error_msg = f"Video generation failed: {str(e)}"
        print(f"❌ Job {job_id} failed. Error: {error_msg}")
        traceback.print_exc()
        video_jobs[job_id].update({
            "status": "failed",
            "message": error_msg
        })

# --- FastAPI Endpoints ---
@app.post("/generate-video", response_model=JobCreationResponse)
async def generate_video_endpoint(request: VideoGenerationRequest, background_tasks: BackgroundTasks):
    job_id = uuid.uuid4().hex
    global video_jobs
    video_jobs[job_id] = {"status": "pending", "message": "Job has been queued.", "video_url": None}
    background_tasks.add_task(create_video_task, job_id, request.content, request.language)
    return {"job_id": job_id, "message": "Video generation job has been started."}

@app.get("/video-status/{job_id}", response_model=JobStatusResponse)
async def get_video_status(job_id: str):
    job = video_jobs.get(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Job ID not found.")
    return JobStatusResponse(job_id=job_id, **job)

@app.get("/")
async def root():
    return {"message": "Industry-Level Video Generation API is running."}