#!/usr/bin/env bash
set -euo pipefail
: "${DPWFMS_LOCAL_USERNAME:?Set DPWFMS_LOCAL_USERNAME to an account with vehicle.manage}"
: "${DPWFMS_LOCAL_PASSWORD:?Set DPWFMS_LOCAL_PASSWORD}"
API_URL="${DPWFMS_API_URL:-http://localhost:8080}"
COUNT="${DPWFMS_MOCK_VEHICLE_COUNT:-12}"

python3 - "$API_URL" "$COUNT" <<'PY'
import base64, datetime, json, os, sys, urllib.error, urllib.request, uuid
base, count = sys.argv[1].rstrip('/'), int(sys.argv[2])
if not 1 <= count <= 1500:
    raise SystemExit('DPWFMS_MOCK_VEHICLE_COUNT must be between 1 and 1500')
credentials = f"{os.environ['DPWFMS_LOCAL_USERNAME']}:{os.environ['DPWFMS_LOCAL_PASSWORD']}"
auth = 'Basic ' + base64.b64encode(credentials.encode()).decode()
now = datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00', 'Z')
for index in range(count):
    asset_id = uuid.uuid5(uuid.NAMESPACE_DNS, f'dpwfms-mock-itv-{index}')
    payload = {
        'messageId': f'mock-position-{asset_id}-{now}', 'schemaVersion': '1.0',
        'correlationId': f'mock-run-{now}', 'occurredAt': now,
        'fleetNumber': f'MOCK-ITV-{index + 1:03d}', 'assetType': 'ITV', 'plantCode': 'JEA',
        'latitude': 24.975 + (index % 4) * .007, 'longitude': 55.015 + (index // 4) * .009,
        'heading': float((index * 31) % 360), 'speedKph': 18.0 if index % 3 else 0.0,
        'energyPercent': float(35 + (index * 5) % 60),
        'operationalStatus': 'IDLE' if index % 3 == 0 else 'WORKING',
        'availabilityStatus': 'AVAILABLE' if index % 3 == 0 else 'ASSIGNED',
        'deviceId': f'MOCK-DEVICE-{index + 1:03d}', 'trackItId': f'MOCK-TRACKIT-{index + 1:03d}'
    }
    request = urllib.request.Request(f'{base}/api/telemetry/assets/{asset_id}/position',
        data=json.dumps(payload).encode(), method='POST',
        headers={'Authorization': auth, 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(request) as response:
            result = json.load(response)
            print(f"{payload['fleetNumber']}: {result['result']}")
    except urllib.error.HTTPError as error:
        raise SystemExit(f"telemetry load failed ({error.code}): {error.read().decode()}")
PY
