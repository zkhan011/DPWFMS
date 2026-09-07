#!/usr/bin/env python3
"""Publish a representative fleet scenario through RabbitMQ's management API."""
import argparse, base64, datetime, json, os, time, urllib.parse, urllib.request, uuid

SCENARIOS = [
    ("MOVING", "WORKING", "ASSIGNED", "DIESEL", 72, 18),
    ("IDLE-EV", "IDLE", "AVAILABLE", "ELECTRIC", 64, 0),
    ("LOW-EV", "IDLE", "AVAILABLE", "ELECTRIC", 12, 0),
    ("BUSY-LOW-EV", "WORKING", "ASSIGNED", "ELECTRIC", 9, 12),
    ("LOW-DIESEL", "IDLE", "AVAILABLE", "DIESEL", 11, 0),
    ("PARKED", "PARKED", "AVAILABLE", "ELECTRIC", 48, 0),
    ("FAULT", "FAULT", "UNAVAILABLE", "DIESEL", 46, 0),
    ("OFFLINE", "OFFLINE", "UNAVAILABLE", "DIESEL", 80, 0),
    ("STALE", "IDLE", "AVAILABLE", "DIESEL", 55, 0),
]

def publish(base, username, password, routing_key, envelope):
    exchange = urllib.parse.quote("dpwfms.business.v1", safe="")
    body = json.dumps({"properties":{"content_type":"application/json"},"routing_key":routing_key,
                       "payload":json.dumps(envelope),"payload_encoding":"string"}).encode()
    request = urllib.request.Request(f"{base.rstrip('/')}/api/exchanges/%2F/{exchange}/publish",
        data=body, headers={"Content-Type":"application/json","Authorization":"Basic "+base64.b64encode(f"{username}:{password}".encode()).decode()})
    with urllib.request.urlopen(request) as response:
        result = json.load(response)
        if not result.get("routed"): raise RuntimeError("message was not routed; check RabbitMQ topology and API startup")

def main():
    parser=argparse.ArgumentParser(description="Simulate moving, idle, charging, fault, offline and stale fleet telemetry")
    parser.add_argument("--url",default=os.getenv("RABBITMQ_MANAGEMENT_URL","http://localhost:15672"))
    parser.add_argument("--user",default=os.getenv("RABBITMQ_USER","dpwfms_app"))
    parser.add_argument("--password",default=os.getenv("RABBITMQ_PASSWORD"),required=os.getenv("RABBITMQ_PASSWORD") is None)
    parser.add_argument("--cycles",type=int,default=1,help="Number of updates; 0 runs continuously")
    parser.add_argument("--interval",type=float,default=5)
    args=parser.parse_args(); cycle=0
    while args.cycles==0 or cycle<args.cycles:
        now=datetime.datetime.now(datetime.timezone.utc)
        for index,(name,status,availability,source,energy,speed) in enumerate(SCENARIOS):
            asset_id=str(uuid.uuid5(uuid.NAMESPACE_DNS,f"dpwfms-rabbit-scenario-{name}"))
            occurred=now-datetime.timedelta(minutes=5) if name=="STALE" else now
            telemetry={"messageId":f"scenario-{name}-{cycle}-{int(now.timestamp())}","schemaVersion":"1.0",
                "correlationId":f"rabbit-simulation-{cycle}","occurredAt":occurred.isoformat().replace("+00:00","Z"),
                "fleetNumber":f"DEMO-{name}","assetType":"ITV","plantCode":"JEA",
                "latitude":24.975+(index%3)*.008+(cycle*.0002 if name=="MOVING" else 0),
                "longitude":55.015+(index//3)*.009,"heading":float((index*37+cycle*8)%360),"speedKph":float(speed),
                "energyPercent":float(max(1,energy-cycle if source=="ELECTRIC" else energy)),"energySource":source,
                "operationalStatus":status,"availabilityStatus":availability,
                "deviceId":f"RABBIT-{name}","trackItId":f"TRACK-{name}"}
            publish(args.url,args.user,args.password,"telemetry.asset.position.v1",{"assetId":asset_id,"telemetry":telemetry})
            print(f"published {telemetry['fleetNumber']}: {status}, {source} {telemetry['energyPercent']}%")
        cycle+=1
        if args.cycles==0 or cycle<args.cycles: time.sleep(args.interval)

if __name__=="__main__": main()
