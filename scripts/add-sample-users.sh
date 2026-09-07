#!/usr/bin/env bash
set -euo pipefail
: "${DPWFMS_LOCAL_USERNAME:?Set DPWFMS_LOCAL_USERNAME to an administrator username}"
: "${DPWFMS_LOCAL_PASSWORD:?Set DPWFMS_LOCAL_PASSWORD to the administrator password}"
: "${DPWFMS_SAMPLE_USER_PASSWORD:?Set DPWFMS_SAMPLE_USER_PASSWORD (minimum 12 characters)}"
API_URL="${DPWFMS_API_URL:-http://localhost:8080}"

python3 - "$API_URL" <<'PY'
import base64, json, os, sys, urllib.error, urllib.request
base = sys.argv[1].rstrip('/')
credentials = f"{os.environ['DPWFMS_LOCAL_USERNAME']}:{os.environ['DPWFMS_LOCAL_PASSWORD']}"
auth = 'Basic ' + base64.b64encode(credentials.encode()).decode()

def request(path, method='GET', payload=None):
    body = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(base + path, data=body, method=method,
        headers={'Authorization': auth, 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req) as response:
            return json.load(response) if response.status != 204 else None
    except urllib.error.HTTPError as error:
        detail = error.read().decode()
        if error.code == 400 and 'username already exists' in detail:
            return 'EXISTS'
        raise SystemExit(f'{method} {path} failed ({error.code}): {detail}')

roles = {role['name']: role['id'] for role in request('/api/admin/roles')}
plant_ids = [plant['id'] for plant in request('/api/workspace/plants')]
samples = [('dispatcher.demo','Demo Dispatcher','DISPATCHER'),
           ('operator.demo','Demo Control Room Operator','CONTROL_ROOM_OPERATOR'),
           ('viewer.demo','Demo Report Viewer','REPORT_VIEWER')]
for username, display_name, role_name in samples:
    result = request('/api/admin/users', 'POST', {
        'username': username, 'displayName': display_name,
        'password': os.environ['DPWFMS_SAMPLE_USER_PASSWORD'], 'serviceAccount': False,
        'roleIds': [roles[role_name]], 'plantIds': plant_ids})
    print(f'{username}: {"already exists" if result == "EXISTS" else "created"}')
PY
