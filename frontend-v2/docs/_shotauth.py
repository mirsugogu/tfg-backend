import sys
from playwright.sync_api import sync_playwright

route = sys.argv[1] if len(sys.argv) > 1 else '/dashboard'
out = sys.argv[2] if len(sys.argv) > 2 else 'shot.png'
w = int(sys.argv[3]) if len(sys.argv) > 3 else 1536
h = int(sys.argv[4]) if len(sys.argv) > 4 else 864

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={'width': w, 'height': h})
    page.goto('http://localhost:5173/login', wait_until='networkidle')
    page.wait_for_timeout(500)
    page.fill('input[name=email]', 'admin@optima.com')
    page.fill('input[name=password]', '12345678')
    page.click('button[type=submit]')
    page.wait_for_url('**/dashboard', timeout=15000)
    page.wait_for_timeout(1500)
    if route != '/dashboard':
        page.goto(f'http://localhost:5173{route}', wait_until='networkidle')
        page.wait_for_timeout(1500)
    page.screenshot(path=out)
    print('OK', out, f'({w}x{h})')
    browser.close()
