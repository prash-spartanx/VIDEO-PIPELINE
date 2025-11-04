import requests

def main():
    url = "http://127.0.0.1:8000/generate-video-test"
    payload = {"content": "Sample press release content.", "language": "en"}
    try:
        r = requests.post(url, json=payload, timeout=15)
        print('Status:', r.status_code)
        try:
            print('JSON response:', r.json())
        except Exception:
            print('Text response:', r.text)
    except Exception as e:
        print('Request failed:', e)

if __name__ == '__main__':
    main()
