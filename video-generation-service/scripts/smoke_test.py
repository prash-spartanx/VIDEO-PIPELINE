import os
import sys
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from main import create_video_file

def run_smoke_test():
    # Use a temp folder inside the project so we can find output easily
    tmp_dir = os.path.join(os.getcwd(), 'tmp_videos')
    os.makedirs(tmp_dir, exist_ok=True)
    output_path = os.path.join(tmp_dir, 'smoke_test_output.mp4')
    script = (
        "This is a short smoke test. The system will generate a few frames and a small audio." 
        " It should produce a playable MP4 without calling any external APIs."
    )
    # language set to english to avoid gTTS issues
    create_video_file(script, output_path, 'en')
    print('Smoke test complete. Output at:', output_path)

if __name__ == '__main__':
    run_smoke_test()
