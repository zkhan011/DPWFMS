import{useEffect,useMemo,useRef,useState}from'react';
import'leaflet/dist/leaflet.css';
import{api}from'../api/client';
import{MapConfiguration,MapHandle,MapLayers,MapVehicle,resolveProvider}from'../map/provider';
import{PageHeader,Status}from'../components/ui';

type RoutingHealth={status:string;reason?:string;graph?:{version:number}};
type RouteInfo={status?:string;graph_version?:number;total_distance_m?:number;estimated_seconds?:number;route_geojson?:string;failureReason?:string};
type CanParameter={Name:string;Value:unknown};
type Telemetry={external_asset_id:string;asset_number:string;make?:string;model?:string;measurement_timestamp:string;source_updated_at:string;received_at:string;latitude?:number;longitude?:number;speed_kph?:number;battery_voltage?:number;fuel_consumption_rate?:number;total_fuel_used?:number;engine_hours?:number;location_mapping_status?:string;location_name?:string;location_code?:string;gps_available:boolean;fuel_data_available:boolean;engine_data_available:boolean;can_data_available:boolean;can_parameters?:CanParameter[];freshness:string;warnings:string[];fuelConsumptionRateUnit?:string};
type Vehicle={id:string;fleet_number:string;asset_type:string;latitude?:number;longitude?:number;heading?:number;speed_kph?:number;operational_status:string;availability_status:string;energy_percent?:number;energy_source?:string;current_job_id?:string;last_telemetry_at?:string;device_id?:string;trackit_id?:string;external_asset_id?:string;gps_available?:boolean;location_name?:string;location_code?:string;freshness?:string;last_valid_gps_at?:string};

const show=(value:unknown,unit='')=>value==null?'Not available':`${String(value)}${unit?` ${unit}`:''}`;
const time=(value?:string)=>value?new Date(value).toLocaleString():'Not available';
const mapErrorMessage=(error:unknown)=>{
 const message=error instanceof Error?error.message:'Map unavailable';
 return /404/.test(message)
  ? 'Map files returned 404. Set MAP_ASSETS_DIRECTORY to the folder containing style.json, dubai_area.pmtiles, and fonts (by default: .\\map-assets).'
  : message;
};

export default function MapPage(){
 const element=useRef<HTMLDivElement>(null),handle=useRef<MapHandle|undefined>(undefined);
 const[provider,setProvider]=useState('Loading'),[vehicles,setVehicles]=useState<Vehicle[]>([]),[selected,setSelected]=useState<Vehicle>(),[telemetry,setTelemetry]=useState<Telemetry>(),[query,setQuery]=useState(''),[status,setStatus]=useState('ALL'),[type,setType]=useState('ALL'),[availability,setAvailability]=useState('ALL'),[freshness,setFreshness]=useState('ALL'),[visible,setVisible]=useState(true),[error,setError]=useState(''),[actionMessage,setActionMessage]=useState(''),[actionBusy,setActionBusy]=useState(false),[routingHealth,setRoutingHealth]=useState<RoutingHealth>({status:'UNKNOWN'}),[routeInfo,setRouteInfo]=useState<RouteInfo>();
 const filtered=useMemo(()=>vehicles.filter(v=>v.latitude!=null&&v.longitude!=null&&v.fleet_number.toLowerCase().includes(query.toLowerCase())&&(status==='ALL'||v.operational_status===status)&&(type==='ALL'||v.asset_type===type)&&(availability==='ALL'||v.availability_status===availability)&&(freshness==='ALL'||v.freshness===freshness)),[vehicles,query,status,type,availability,freshness]);
 const positions=useMemo<MapVehicle[]>(()=>filtered.map(v=>({name:v.fleet_number,latitude:v.latitude!,longitude:v.longitude!,heading:v.heading,speedKph:v.speed_kph,status:v.freshness??v.operational_status,availability:v.availability_status,gpsAvailable:v.gps_available,locationName:v.location_name,locationCode:v.location_code,energyPercent:v.energy_percent,energySource:v.energy_source,stale:v.freshness?v.freshness!=='ONLINE':!v.last_telemetry_at||Date.now()-new Date(v.last_telemetry_at).getTime()>60000,onSelect:()=>select(v)})),[filtered]);
 async function select(v:Vehicle){
  setSelected(v);setTelemetry(undefined);setActionMessage('');
  const externalId=v.external_asset_id??v.trackit_id;
  const telemetryRequest=externalId?api<Telemetry>(`/api/v1/integrations/trackit/telemetry/${encodeURIComponent(externalId)}`).then(setTelemetry).catch(()=>setTelemetry(undefined)):Promise.resolve();
  try{const route=await api<RouteInfo>(`/api/routing/assets/${v.id}/current-route`);setRouteInfo(route);if(route.route_geojson){const feature=JSON.parse(route.route_geojson);handle.current?.setTrail(feature.geometry.coordinates.map((p:number[])=>({latitude:p[1],longitude:p[0]})))}else{const trail=await api<{latitude:number;longitude:number}[]>(`/api/workspace/vehicles/${v.id}/trail?minutes=60`);handle.current?.setTrail(trail)}}catch(e){setRouteInfo({status:'UNAVAILABLE',failureReason:e instanceof Error?e.message:'ROUTE_UNAVAILABLE'});handle.current?.setTrail([])}
  await telemetryRequest;
 }
 async function dispatch(kind:'evaluate'|'fueling'|'charging'){
  if(!selected)return;setActionBusy(true);setActionMessage('');
  try{
   if(kind==='charging')await api('/api/charging/assignments',{method:'POST',body:JSON.stringify({assetId:selected.id,mode:'AUTOMATIC',reason:'Requested from live telemetry map',idempotencyKey:`map-charging-${selected.id}`})});
   else await api(`/api/automation/assets/${selected.id}/${kind}`,{method:'POST'});
   setActionMessage(kind==='evaluate'?'Configured automation evaluated. Open Jobs to review the decision.':`Vehicle sent for ${kind}.`);
  }catch(e){setActionMessage(e instanceof Error?e.message:`Unable to start ${kind}`)}finally{setActionBusy(false)}
 }
 function resetFilters(){setQuery('');setStatus('ALL');setType('ALL');setAvailability('ALL');setFreshness('ALL')}
 async function enterFullscreen(){
  const target=element.current?.parentElement;
  if(!target)return;
  try{await target.requestFullscreen()}catch(e){setError(mapErrorMessage(e))}
 }
 useEffect(()=>{handle.current?.setVehicles(positions)},[positions]);
 useEffect(()=>{let active=true,timer:number;async function refresh(){try{const[rows,trackit]=await Promise.all([api<Vehicle[]>('/api/workspace/vehicles'),api<Vehicle[]>('/api/v1/integrations/trackit/telemetry/map/positions').catch(()=>[])]);const live=new Map(trackit.map(item=>[item.id,item]));if(active){setVehicles(rows.map(row=>({...row,...live.get(row.id),operational_status:row.operational_status,availability_status:row.availability_status})));setError('')}}catch(e){if(active)setError(mapErrorMessage(e))}}async function start(){try{const[config,layers,health]=await Promise.all([api<MapConfiguration>('/api/workspace/map-configuration'),api<MapLayers>('/api/workspace/map-operational-layers'),api<RoutingHealth>('/api/routing/health')]);setRoutingHealth(health);if(!active||!element.current)return;const adapter=resolveProvider(config);setProvider(adapter.name);handle.current=await adapter.mount(element.current,config);handle.current.setOperationalLayers(layers);await refresh();timer=window.setInterval(refresh,5000)}catch(e){if(active)setError(mapErrorMessage(e))}}start();return()=>{active=false;clearInterval(timer);handle.current?.destroy()}},[]);
 useEffect(()=>handle.current?.setVehiclesVisible(visible),[visible]);
 const options=(key:keyof Vehicle)=>[...new Set(vehicles.map(v=>String(v[key])).filter(Boolean))].sort();
 return <>
  <PageHeader eyebrow="LIVE OPERATIONS" title="Live fleet map"
    description={`${provider} · ${positions.length} visible assets · routing ${routingHealth.status}${routingHealth.graph?` graph v${routingHealth.graph.version}`:''}`}
    actions={<button type="button" className="primary" onClick={()=>void enterFullscreen()} aria-label="Open fleet map in full screen">Full screen</button>}/>
  {error&&<div className="notice error map-error" role="alert">{error}</div>}
  <div className="map-layout map-workspace">
   <aside className="map-controls" aria-labelledby="map-filter-title">
    <div className="map-controls-head">
     <div><p className="map-kicker">LIVE FLEET</p><h2 id="map-filter-title">Find vehicles</h2></div>
     <button className="map-reset" type="button" onClick={resetFilters}>Reset</button>
    </div>
    <p className="map-help">Filter the map by asset, status, or telemetry freshness.</p>
    <div className="map-filter-grid">
     <label htmlFor="map-search">Fleet number
      <input id="map-search" type="search" placeholder="Search fleet number" value={query} onChange={e=>setQuery(e.target.value)}/>
     </label>
     <label htmlFor="map-type">Asset type
      <select id="map-type" value={type} onChange={e=>setType(e.target.value)}><option value="ALL">All types</option>{options('asset_type').map(x=><option key={x} value={x}>{x}</option>)}</select>
     </label>
     <label htmlFor="map-status">Operational status
      <select id="map-status" value={status} onChange={e=>setStatus(e.target.value)}><option value="ALL">All statuses</option>{options('operational_status').map(x=><option key={x} value={x}>{x.replaceAll('_',' ')}</option>)}</select>
     </label>
     <label htmlFor="map-availability">Availability
      <select id="map-availability" value={availability} onChange={e=>setAvailability(e.target.value)}><option value="ALL">All availability</option>{options('availability_status').map(x=><option key={x} value={x}>{x.replaceAll('_',' ')}</option>)}</select>
     </label>
     <label htmlFor="map-freshness">Telemetry freshness
      <select id="map-freshness" value={freshness} onChange={e=>setFreshness(e.target.value)}><option value="ALL">Any freshness</option><option value="ONLINE">Online</option><option value="STALE">Stale</option><option value="OFFLINE">Offline</option></select>
     </label>
    </div>
    <label className="map-visibility"><input type="checkbox" checked={visible} onChange={e=>setVisible(e.target.checked)}/> Show vehicle markers</label>
    <section className="map-snapshot" aria-labelledby="map-snapshot-title">
     <h3 id="map-snapshot-title">Fleet snapshot</h3>
     <dl>
      <dt>Registered assets</dt><dd>{vehicles.length}</dd>
      <dt>Visible on map</dt><dd>{positions.length}</dd>
      <dt>Stale / offline</dt><dd>{vehicles.filter(v=>v.freshness==='STALE'||v.freshness==='OFFLINE'||(!v.freshness&&(!v.last_telemetry_at||Date.now()-new Date(v.last_telemetry_at).getTime()>60000))).length}</dd>
      <dt>Low energy · 20% or less</dt><dd>{vehicles.filter(v=>(v.energy_percent??100)<=20).length}</dd>
     </dl>
    </section>
    <p className="map-help map-refresh-help">Positions refresh automatically every 5 seconds.</p>
   </aside>
   <section className="map-canvas-panel" aria-label="Fleet map">
    <header className="map-canvas-toolbar">
     <div>
      <p className="map-live-title"><span className="map-live-dot" aria-hidden="true"/>{provider==='Loading'?'Connecting to map…':'Live vehicle positions'}</p>
      <p className="map-refresh-status" role="status" aria-live="polite">{positions.length} of {vehicles.length} registered assets shown</p>
     </div>
     <div className="map-legend" aria-label="Vehicle status legend">
      <span><i className="legend-online"/>Online</span><span><i className="legend-stale"/>Stale</span><span><i className="legend-fault"/>Fault</span><span><i className="legend-offline"/>Offline</span>
     </div>
    </header>
    {!error&&vehicles.length>0&&positions.length===0&&<div className="map-empty-state" role="status">No vehicles match these filters. Reset filters or choose another status.</div>}
    {!error&&vehicles.length===0&&provider!=='Loading'&&<div className="map-empty-state" role="status">No positioned vehicles are available yet. The map will update when telemetry arrives.</div>}
    <div ref={element} className="operational-map" role="region" aria-label="Interactive map showing filtered fleet positions" tabIndex={0}/>
    <p className="map-sr-only">Use the map zoom controls to explore. Select a vehicle marker to view its telemetry and actions.</p>
   </section>
   {selected&&<AssetDrawer vehicle={selected} telemetry={telemetry} route={routeInfo} busy={actionBusy} message={actionMessage} dispatch={dispatch} close={()=>{setSelected(undefined);setTelemetry(undefined);handle.current?.setTrail([])}}/>}
  </div>
 </>
}

function AssetDrawer({vehicle:v,telemetry:t,route,busy,message,dispatch,close}:{vehicle:Vehicle;telemetry?:Telemetry;route?:RouteInfo;busy:boolean;message:string;dispatch:(kind:'evaluate'|'fueling'|'charging')=>void;close:()=>void}){
 const can=t?.can_parameters??[];const canValue=(...names:string[])=>{const item=can.find(p=>names.some(name=>p.Name.toLowerCase()===name.toLowerCase()));return item?.Value};const fuelLevel=canValue('Fuel Level');
 return <aside className="asset-drawer telemetry-map-drawer"><header><div><small>LIVE ASSET DETAIL</small><h2>{v.fleet_number}</h2><span>{[t?.make,t?.model].filter(Boolean).join(' ')||v.asset_type}</span></div><button onClick={close} aria-label="Close details">×</button></header><div className="drawer-status"><Status value={t?.freshness??v.operational_status}/><Status value={v.availability_status}/></div>{t?.warnings?.length?<div className="notice warning">{t.warnings.join(' · ')}</div>:null}<h3>Position and identity</h3><dl><dt>Position</dt><dd>{show(v.latitude)}, {show(v.longitude)}</dd><dt>GPS</dt><dd>{t?.gps_available===false?'Unavailable — showing last valid fix':'Live fix'}</dd><dt>Speed</dt><dd>{show(t?.speed_kph??v.speed_kph,'km/h')}</dd><dt>Mapped location</dt><dd>{[t?.location_name,t?.location_code,t?.location_mapping_status].filter(Boolean).join(' · ')||'Not mapped'}</dd><dt>Measurement</dt><dd>{time(t?.measurement_timestamp??v.last_telemetry_at)}</dd><dt>TrackIT / device</dt><dd>{t?.external_asset_id??v.trackit_id??v.device_id??'Unknown'}</dd></dl><h3>Energy, fuel and engine</h3><dl><dt>Energy profile</dt><dd>{show(v.energy_percent,'%')} {v.energy_source??''}</dd><dt>Battery voltage</dt><dd>{show(t?.battery_voltage,'V')}</dd><dt>Fuel level</dt><dd>{show(fuelLevel,'%')}</dd><dt>Fuel rate</dt><dd>{show(t?.fuel_consumption_rate,t?.fuelConsumptionRateUnit)}</dd><dt>Total fuel used</dt><dd>{show(t?.total_fuel_used)}</dd><dt>Engine hours</dt><dd>{show(t?.engine_hours,'hours')}</dd><dt>Engine speed</dt><dd>{show(canValue('Engine Speed'),'rpm')}</dd><dt>Engine load</dt><dd>{show(canValue('Engine Percent Load at Current Speed','Engine Load'),'%')}</dd><dt>Coolant</dt><dd>{show(canValue('Engine Coolant Temperature','Coolant Temperature'),'°C')}</dd></dl><h3>Transmission and CAN</h3><dl><dt>Current / requested gear</dt><dd>{show(canValue('Current Gear'))} / {show(canValue('Selected / Requested Gear','Requested Gear'))}</dd><dt>Input / output shaft</dt><dd>{show(canValue('Input Shaft Speed'),'rpm')} / {show(canValue('Output Shaft Speed'),'rpm')}</dd><dt>Transmission oil</dt><dd>{show(canValue('Transmission Oil Temperature'),'°C')}</dd><dt>Trip / total odometer</dt><dd>{show(canValue('Trip Odometer'),'km')} / {show(canValue('Total Odometer'),'km')}</dd><dt>CAN channels</dt><dd>{t?.can_data_available?`${can.length} available`:'Unavailable'}</dd></dl><h3>Current operation</h3><dl><dt>Current job</dt><dd>{v.current_job_id??'None'}</dd><dt>Route graph</dt><dd>{route?.graph_version?`v${route.graph_version}`:'Unavailable'}</dd><dt>Route distance / ETA</dt><dd>{route?.total_distance_m?`${Math.round(route.total_distance_m)} m`:'—'} / {route?.estimated_seconds?`${Math.round(route.estimated_seconds/60)} min`:'—'}</dd></dl>{(route?.failureReason||route?.status==='UNAVAILABLE')&&<div className="notice error">Route unavailable: {route.failureReason??'NO_PERSISTED_ROUTE'}</div>}<div className="operation-actions"><button className="primary" disabled={busy} onClick={()=>dispatch('evaluate')}>Run configured automation</button><button disabled={busy||v.energy_source==='ELECTRIC'} onClick={()=>dispatch('fueling')}>Send to fueling</button><button disabled={busy||v.energy_source!=='ELECTRIC'} onClick={()=>dispatch('charging')}>Send to charging</button></div><small className="muted">Configured automation applies active safety rules, thresholds, capacity and route eligibility before creating work.</small>{message&&<div className={message.toLowerCase().includes('unable')?'notice error':'notice'}>{message}</div>}<a className="button drawer-jobs-link" href="#/jobs">Open jobs and dispatch →</a></aside>
}
