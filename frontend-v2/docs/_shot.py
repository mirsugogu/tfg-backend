import sys
from playwright.sync_api import sync_playwright

url = sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:5173/login'
out = sys.argv[2] if len(sys.argv) > 2 else 'shot.png'
w = int(sys.argv[3]) if len(sys.argv) > 3 else 1536
h = int(sys.argv[4]) if len(sys.argv) > 4 else 864

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={'width': w, 'height': h})
    page.goto(url, wait_until='networkidle')
    page.wait_for_timeout(1000)
    page.screenshot(path=out)
    print(f'OK {out} ({w}x{h})')
    browser.close()
