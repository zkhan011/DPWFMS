import L from 'leaflet';

export type MapConfiguration={provider:'google'|'osm'|'offline'|'mapbox'|'pmtiles';default_latitude:number;default_longitude:number;default_zoom:number;tile_url?:string;style_url?:string};
export type MapVehicle={name:string;latitude:number;longitude:number;status:string;stale:boolean;heading?:number;speedKph?:number;energyPercent?:number;energySource?:string;availability?:string;gpsAvailable?:boolean;locationName?:string;locationCode?:string;onSelect?:()=>void};
export type MapLayers={parkingZones:{boundary?:unknown;code:string}[];parkingBays:{latitude?:number;longitude?:number;status:string;code:string}[];chargingStations:{latitude:number;longitude:number;status:string;code:string}[];fuelingStations:{latitude:number;longitude:number;status:string;code:string}[];geofences:{boundary:unknown;code:string;restricted:boolean}[]};
export interface MapHandle { setVehicles(vehicles:MapVehicle[]):void; setVehiclesVisible(visible:boolean):void; setTrail(points:{latitude:number;longitude:number}[]):void; setOperationalLayers(layers:MapLayers):void; invalidateSize():void; destroy():void; }
export interface MapProvider { readonly name:string; mount(element:HTMLElement,configuration:MapConfiguration):Promise<MapHandle>; }

class LeafletRasterProvider implements MapProvider {
  constructor(public readonly name:string,private readonly tile:(configuration:MapConfiguration)=>string){}

  async mount(element:HTMLElement,configuration:MapConfiguration):Promise<MapHandle>{
    // React development mode deliberately mounts components twice. Always clear a
    // potentially interrupted Leaflet mount before creating the real map.
    element.replaceChildren();
    const map=L.map(element,{preferCanvas:true,zoomControl:true}).setView(
      [configuration.default_latitude,configuration.default_longitude],configuration.default_zoom);
    L.tileLayer(this.tile(configuration),{maxZoom:20,attribution:'© OpenStreetMap contributors'}).addTo(map);
    const vehicleLayer=L.layerGroup().addTo(map),trailLayer=L.layerGroup().addTo(map),operationsLayer=L.layerGroup().addTo(map);
    let visible=true,initialFit=true,currentVehicles:MapVehicle[]=[];
    const statusColor=(status:string,stale:boolean)=>['OFFLINE','OUT_OF_SERVICE'].includes(status)?'#7a8792':stale?'#d68100':(['FAULT','CRITICAL'].includes(status)?'#d9253f':['IDLE','PARKED','QUEUED'].includes(status)?'#e59a19':['ASSIGNED','WORKING','CHARGING','EN_ROUTE'].includes(status)?'#1684d8':'#2aa866');
    const render=()=>{vehicleLayer.clearLayers();const size=Math.max(.00008,4/Math.pow(2,map.getZoom()));const groups=new Map<string,MapVehicle[]>();currentVehicles.forEach(v=>{const key=`${Math.round(v.latitude/size)}:${Math.round(v.longitude/size)}`;groups.set(key,[...(groups.get(key)||[]),v])});groups.forEach(group=>{if(group.length>1){const lat=group.reduce((n,v)=>n+v.latitude,0)/group.length,lon=group.reduce((n,v)=>n+v.longitude,0)/group.length;L.marker([lat,lon],{icon:L.divIcon({className:'asset-cluster',html:`<span>${group.length}</span>`,iconSize:[34,34]})}).bindTooltip(`${group.length} assets`).on('click',()=>map.setZoomAround([lat,lon],map.getZoom()+2)).addTo(vehicleLayer)}else{const v=group[0],color=statusColor(v.status,v.stale);L.marker([v.latitude,v.longitude],{icon:L.divIcon({className:'asset-marker',html:`<span style="--marker:${color};transform:rotate(${v.heading||0}deg)">▲</span>`,iconSize:[28,28]})}).bindTooltip(v.name,{direction:'top'}).bindPopup(`<b>${escapeHtml(v.name)}</b><br>${escapeHtml(v.status)} · ${v.speedKph??0} km/h<br>${v.gpsAvailable===false?'GPS unavailable · retained position':escapeHtml(v.locationName||v.locationCode||'Location unavailable')}<br><a href="#/telemetry">Fleet telemetry details</a>`).on('click',()=>v.onSelect?.()).addTo(vehicleLayer)}})};map.on('zoomend',render);

    const handle:MapHandle={
      setVehicles(vehicles){
        currentVehicles=vehicles;render();
        if(initialFit&&visible&&vehicles.length>0){const bounds=L.latLngBounds(vehicles.map(v=>[v.latitude,v.longitude]));if(bounds.isValid())map.fitBounds(bounds.pad(.15),{maxZoom:configuration.default_zoom});initialFit=false}
      },
      setVehiclesVisible(nextVisible){
        visible=nextVisible;
        if(nextVisible&&!map.hasLayer(vehicleLayer))vehicleLayer.addTo(map);
        if(!nextVisible&&map.hasLayer(vehicleLayer))map.removeLayer(vehicleLayer);
      },
      setTrail(points){trailLayer.clearLayers();if(points.length>1)L.polyline(points.map(p=>[p.latitude,p.longitude]),{color:'#d71968',weight:4}).addTo(trailLayer)},
      setOperationalLayers(layers){operationsLayer.clearLayers();const marker=(lat:number,lon:number,label:string,color:string)=>L.circleMarker([lat,lon],{radius:6,color,fillOpacity:.9}).bindTooltip(label).addTo(operationsLayer);layers.parkingBays.filter(x=>x.latitude!=null).forEach(x=>marker(x.latitude!,x.longitude!,`Parking ${x.code} · ${x.status}`,'#2aa866'));layers.chargingStations.forEach(x=>marker(x.latitude,x.longitude,`Charging ${x.code} · ${x.status}`,'#1684d8'));layers.fuelingStations.forEach(x=>marker(x.latitude,x.longitude,`Fuel ${x.code} · ${x.status}`,'#9b5cff'));[...layers.parkingZones,...layers.geofences].forEach(x=>{try{const geo=typeof x.boundary==='string'?JSON.parse(x.boundary):x.boundary;if(geo)L.geoJSON(geo as never,{style:{color:'restricted'in x&&x.restricted?'#d9253f':'#d71968',weight:2,fillOpacity:.08}}).bindTooltip(x.code).addTo(operationsLayer)}catch{}})},
      invalidateSize(){map.invalidateSize()},
      destroy(){map.remove();element.replaceChildren()}
    };
    requestAnimationFrame(()=>handle.invalidateSize());
    return handle;
  }
}

class GoogleProvider implements MapProvider {
  readonly name='Google Maps';
  async mount(element:HTMLElement,configuration:MapConfiguration):Promise<MapHandle>{
    const key=import.meta.env.VITE_GOOGLE_MAPS_API_KEY;
    await loadGoogle(key);
    const googleApi=(window as unknown as {google:{maps:{Map:new(e:HTMLElement,o:object)=>unknown;Marker:new(o:object)=>{setMap:(map:unknown|null)=>void}}}}).google;
    const map=new googleApi.maps.Map(element,{center:{lat:configuration.default_latitude,lng:configuration.default_longitude},zoom:configuration.default_zoom,mapTypeControl:false});
    const markers:{setMap:(map:unknown|null)=>void}[]=[];
    return {
      setVehicles(vehicles){markers.forEach(marker=>marker.setMap(null));markers.length=0;vehicles.forEach(vehicle=>markers.push(new googleApi.maps.Marker({map,position:{lat:vehicle.latitude,lng:vehicle.longitude},title:`${vehicle.name} · ${vehicle.status}`})))},
      setVehiclesVisible(visible){markers.forEach(marker=>marker.setMap(visible?map:null))},
      setTrail(){},setOperationalLayers(){},
      invalidateSize(){},
      destroy(){markers.forEach(marker=>marker.setMap(null));element.replaceChildren()}
    };
  }
}

let googlePromise:Promise<void>|undefined;
function loadGoogle(key:string):Promise<void>{if(googlePromise)return googlePromise;googlePromise=new Promise((resolve,reject)=>{const script=document.createElement('script');script.src=`https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(key)}`;script.async=true;script.onload=()=>resolve();script.onerror=()=>reject(new Error('Google Maps could not be loaded'));document.head.appendChild(script)});return googlePromise}
function escapeHtml(value:string){return value.replace(/[&<>'"]/g,character=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[character]!))}


import { addProtocol, Map as MapLibreMap, Popup } from 'maplibre-gl';
import { Protocol as PmtilesProtocol } from 'pmtiles';
import 'maplibre-gl/dist/maplibre-gl.css';

let pmtilesProtocol: PmtilesProtocol | undefined;
function installPmtilesProtocol() {
  if (!pmtilesProtocol) pmtilesProtocol = new PmtilesProtocol();
  try { addProtocol('pmtiles', pmtilesProtocol.tile); } catch { /* already registered by another map instance */ }
}

type GeoFeature = { type: 'Feature'; geometry: { type: string; coordinates: unknown }; properties: Record<string, unknown> };
const vehicleSourceId = 'dpwfms-vehicles';
const operationsSourceId = 'dpwfms-operations';
const trailSourceId = 'dpwfms-trail';

class PmtilesProvider implements MapProvider {
  readonly name = 'PMTiles (MapLibre)';
  async mount(element: HTMLElement, configuration: MapConfiguration): Promise<MapHandle> {
    element.replaceChildren();
    const styleUrl = configuration.style_url || import.meta.env.VITE_PMTILES_STYLE_URL || import.meta.env.VITE_OFFLINE_STYLE_URL || '/maps/style.json';
    installPmtilesProtocol();
    const styleBase = new URL('.', new URL(styleUrl, window.location.href)).href;
    const map = new MapLibreMap({
      container: element,
      style: styleUrl,
      center: [configuration.default_longitude, configuration.default_latitude],
      zoom: configuration.default_zoom,
      transformRequest: (url) => {
        if (!url.startsWith('pmtiles://')) return { url };
        const archivePath = url.slice('pmtiles://'.length);
        const archiveUrl = /^https?:\/\//i.test(archivePath) ? archivePath : new URL(archivePath.replace(/^\/+/, ''), styleBase).href;
        return { url: 'pmtiles://' + archiveUrl };
      }
    });
    await new Promise<void>((resolve, reject) => {
      map.once('load', () => resolve());
      map.once('error', event => reject(event.error ?? new Error('PMTiles map style could not be loaded')));
    });

    const emptyCollection = () => ({ type: 'FeatureCollection' as const, features: [] as GeoFeature[] });
    map.addSource(vehicleSourceId, { type: 'geojson', data: emptyCollection(), cluster: true, clusterRadius: 48, clusterMaxZoom: 15 });
    map.addLayer({ id: 'dpwfms-vehicle-clusters', type: 'circle', source: vehicleSourceId, filter: ['has', 'point_count'], paint: { 'circle-color': '#183c64', 'circle-radius': ['step', ['get', 'point_count'], 18, 10, 22, 50, 28], 'circle-stroke-color': '#ffffff', 'circle-stroke-width': 2 } });
    map.addLayer({ id: 'dpwfms-vehicle-cluster-count', type: 'symbol', source: vehicleSourceId, filter: ['has', 'point_count'], layout: { 'text-field': ['get', 'point_count_abbreviated'], 'text-size': 12 }, paint: { 'text-color': '#ffffff' } });

    const arrow = document.createElement('canvas');
    arrow.width = 32; arrow.height = 32;
    const context = arrow.getContext('2d');
    if (context) {
      context.fillStyle = '#ffffff';
      context.beginPath(); context.moveTo(16, 2); context.lineTo(28, 29); context.lineTo(16, 23); context.lineTo(4, 29); context.closePath(); context.fill();
      map.addImage('dpwfms-vehicle-arrow', arrow, { sdf: true });
    }
    map.addLayer({ id: 'dpwfms-vehicle-halo', type: 'circle', source: vehicleSourceId, filter: ['!', ['has', 'point_count']], paint: { 'circle-radius': 10, 'circle-color': ['get', 'markerColor'], 'circle-opacity': 0.22 } });
    map.addLayer({ id: 'dpwfms-vehicle-symbols', type: 'symbol', source: vehicleSourceId, filter: ['!', ['has', 'point_count']], layout: { 'icon-image': 'dpwfms-vehicle-arrow', 'icon-size': 0.7, 'icon-rotate': ['get', 'heading'], 'icon-rotation-alignment': 'map', 'icon-allow-overlap': true, 'text-field': ['get', 'name'], 'text-size': 11, 'text-offset': [0, 1.4], 'text-anchor': 'top', 'text-allow-overlap': false }, paint: { 'icon-color': ['get', 'markerColor'], 'text-color': '#172b3a', 'text-halo-color': '#ffffff', 'text-halo-width': 1.2 } });

    map.addSource(operationsSourceId, { type: 'geojson', data: emptyCollection() });
    map.addLayer({ id: 'dpwfms-operation-fills', type: 'fill', source: operationsSourceId, filter: ['in', ['get', 'shape'], ['literal', ['zone', 'geofence']]], paint: { 'fill-color': ['get', 'color'], 'fill-opacity': 0.1 } });
    map.addLayer({ id: 'dpwfms-operation-lines', type: 'line', source: operationsSourceId, filter: ['in', ['get', 'shape'], ['literal', ['zone', 'geofence']]], paint: { 'line-color': ['get', 'color'], 'line-width': 2, 'line-dasharray': [2, 2] } });
    map.addLayer({ id: 'dpwfms-operation-points', type: 'circle', source: operationsSourceId, filter: ['==', ['geometry-type'], 'Point'], paint: { 'circle-radius': 7, 'circle-color': ['get', 'color'], 'circle-stroke-color': '#ffffff', 'circle-stroke-width': 1.5 } });
    map.addLayer({ id: 'dpwfms-operation-labels', type: 'symbol', source: operationsSourceId, filter: ['==', ['geometry-type'], 'Point'], layout: { 'text-field': ['get', 'label'], 'text-size': 10, 'text-offset': [0, 1.2], 'text-anchor': 'top' }, paint: { 'text-color': '#183247', 'text-halo-color': '#ffffff', 'text-halo-width': 1.2 } });
    map.addSource(trailSourceId, { type: 'geojson', data: { type: 'FeatureCollection', features: [] } });
    map.addLayer({ id: 'dpwfms-trail-line', type: 'line', source: trailSourceId, paint: { 'line-color': '#d71968', 'line-width': 4, 'line-opacity': 0.9 } });

    const colors: Record<string, string> = { OFFLINE: '#7a8792', OUT_OF_SERVICE: '#7a8792', STALE: '#d68100', FAULT: '#d9253f', CRITICAL: '#d9253f', IDLE: '#e59a19', PARKED: '#e59a19', QUEUED: '#e59a19', ASSIGNED: '#1684d8', WORKING: '#1684d8', CHARGING: '#1684d8', EN_ROUTE: '#1684d8' };
    const markerColor = (vehicle: MapVehicle) => vehicle.stale ? '#d68100' : (colors[vehicle.status] || '#2aa866');
    let currentVehicles: MapVehicle[] = [];
    let visible = true;
    let initialFit = true;
    let popup: Popup | undefined;
    const byName = new Map<string, MapVehicle>();
    const setSourceData = (id: string, data: unknown) => {
      const source = map.getSource(id) as maplibregl.GeoJSONSource | undefined;
      source?.setData(data as never);
    };
    const renderVehicles = (vehicles: MapVehicle[]) => {
      currentVehicles = vehicles;
      byName.clear();
      const data = { type: 'FeatureCollection', features: vehicles.map((vehicle, index) => {
        byName.set(vehicle.name, vehicle);
        return { type: 'Feature', id: index, geometry: { type: 'Point', coordinates: [vehicle.longitude, vehicle.latitude] }, properties: { name: vehicle.name, status: vehicle.status, stale: vehicle.stale, heading: vehicle.heading || 0, speed: vehicle.speedKph ?? 0, gps: vehicle.gpsAvailable !== false, location: vehicle.locationName || vehicle.locationCode || 'Location unavailable', markerColor: markerColor(vehicle) } };
      }) };
      setSourceData(vehicleSourceId, data);
    };
    const clickVehicle = (event: maplibregl.MapLayerMouseEvent) => {
      const feature = event.features?.[0];
      if (!feature) return;
      const name = String(feature.properties?.name ?? '');
      const vehicle = byName.get(name);
      if (!vehicle) return;
      const properties = feature.properties ?? {};
      const content = document.createElement('div');
      content.className = 'dpwfms-map-popup';
      const title = document.createElement('strong'); title.textContent = name; content.append(title);
      const details = document.createElement('div');
      details.textContent = String(properties.status) + ' · ' + String(properties.speed) + ' km/h';
      content.append(details);
      const position = document.createElement('div');
      position.textContent = properties.gps === false || properties.gps === 'false' ? 'GPS unavailable · retained position' : String(properties.location);
      content.append(position);
      const link = document.createElement('a'); link.href = '#/telemetry'; link.textContent = 'Fleet telemetry details'; content.append(link);
      popup?.remove();
      popup = new Popup({ closeButton: true, offset: 16 }).setLngLat(event.lngLat).setDOMContent(content).addTo(map);
      vehicle.onSelect?.();
    };
    map.on('click', 'dpwfms-vehicle-symbols', clickVehicle);
    map.on('click', 'dpwfms-vehicle-clusters', event => {
      const feature = event.features?.[0];
      const clusterId = feature?.properties?.cluster_id;
      if (clusterId == null) return;
      const source = map.getSource(vehicleSourceId) as maplibregl.GeoJSONSource;
      source.getClusterExpansionZoom(Number(clusterId), (error, zoom) => {
        if (!error && zoom != null && feature?.geometry.type === 'Point') map.easeTo({ center: feature.geometry.coordinates as [number, number], zoom });
      });
    });
    map.on('mouseenter', 'dpwfms-vehicle-symbols', () => { map.getCanvas().style.cursor = 'pointer'; });
    map.on('mouseleave', 'dpwfms-vehicle-symbols', () => { map.getCanvas().style.cursor = ''; });

    const normalizeGeometry = (value: unknown): unknown => {
      if (typeof value === 'string') { try { return normalizeGeometry(JSON.parse(value)); } catch { return undefined; } }
      if (!value || typeof value !== 'object') return undefined;
      const candidate = value as { type?: string; geometry?: unknown; features?: unknown[] };
      if (candidate.type === 'FeatureCollection' && Array.isArray(candidate.features)) return candidate.features;
      if (candidate.type === 'Feature') return [candidate];
      if (candidate.type && 'coordinates' in candidate) return [{ type: 'Feature', geometry: candidate, properties: {} }];
      if (candidate.geometry) return normalizeGeometry(candidate.geometry);
      return undefined;
    };
    const makeOperationFeature = (geometry: unknown, label: string, color: string, shape: string, restricted = false): GeoFeature[] => {
      const features = normalizeGeometry(geometry);
      if (!Array.isArray(features)) return [];
      return features.flatMap(item => {
        const feature = item as { geometry?: { type: string; coordinates: unknown }; properties?: Record<string, unknown> };
        if (!feature.geometry?.type) return [];
        return [{ type: 'Feature', geometry: feature.geometry, properties: { ...(feature.properties ?? {}), label, color, shape, restricted } }];
      });
    };
    const handle: MapHandle = {
      setVehicles(vehicles) {
        renderVehicles(vehicles);
        if (initialFit && visible && vehicles.length) {
          if (vehicles.length === 1) map.easeTo({ center: [vehicles[0].longitude, vehicles[0].latitude], zoom: Math.max(configuration.default_zoom, 14) });
          else map.fitBounds(new MapLibreMap.LngLatBounds(vehicles.map(vehicle => [vehicle.longitude, vehicle.latitude] as [number, number])), { padding: 56, maxZoom: configuration.default_zoom });
          initialFit = false;
        }
      },
      setVehiclesVisible(nextVisible) {
        visible = nextVisible;
        ['dpwfms-vehicle-clusters', 'dpwfms-vehicle-cluster-count', 'dpwfms-vehicle-halo', 'dpwfms-vehicle-symbols'].forEach(id => map.setLayoutProperty(id, 'visibility', nextVisible ? 'visible' : 'none'));
      },
      setTrail(points) {
        const data = points.length > 1 ? { type: 'Feature', geometry: { type: 'LineString', coordinates: points.map(point => [point.longitude, point.latitude]) }, properties: {} } : { type: 'FeatureCollection', features: [] };
        setSourceData(trailSourceId, data);
      },
      setOperationalLayers(layers) {
        const features: GeoFeature[] = [];
        layers.parkingBays.filter(item => item.latitude != null && item.longitude != null).forEach(item => features.push({ type: 'Feature', geometry: { type: 'Point', coordinates: [item.longitude!, item.latitude!] }, properties: { label: 'Parking ' + item.code + ' · ' + item.status, color: '#2aa866', shape: 'point' } }));
        layers.chargingStations.forEach(item => features.push({ type: 'Feature', geometry: { type: 'Point', coordinates: [item.longitude, item.latitude] }, properties: { label: 'Charging ' + item.code + ' · ' + item.status, color: '#1684d8', shape: 'point' } }));
        layers.fuelingStations.forEach(item => features.push({ type: 'Feature', geometry: { type: 'Point', coordinates: [item.longitude, item.latitude] }, properties: { label: 'Fuel ' + item.code + ' · ' + item.status, color: '#9b5cff', shape: 'point' } }));
        layers.parkingZones.forEach(item => features.push(...makeOperationFeature(item.boundary, item.code, '#d71968', 'zone')));
        layers.geofences.forEach(item => features.push(...makeOperationFeature(item.boundary, item.code, item.restricted ? '#d9253f' : '#d71968', 'geofence', item.restricted)));
        setSourceData(operationsSourceId, { type: 'FeatureCollection', features });
      },
      invalidateSize() { map.resize(); },
      destroy() { popup?.remove(); map.remove(); element.replaceChildren(); }
    };
    requestAnimationFrame(() => handle.invalidateSize());
    return handle;
  }
}

const offline=new LeafletRasterProvider('Offline tiles',configuration=>configuration.tile_url||import.meta.env.VITE_OFFLINE_TILE_URL||'/tiles/{z}/{x}/{y}.png');
const osm=new LeafletRasterProvider('OpenStreetMap',configuration=>configuration.tile_url||import.meta.env.VITE_OSM_TILE_URL||'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png');
const mapbox=new LeafletRasterProvider('Mapbox-compatible',configuration=>configuration.tile_url||'/tiles/{z}/{x}/{y}.png');

export function resolveProvider(configuration:MapConfiguration):MapProvider {
  if(configuration.provider==='pmtiles')return new PmtilesProvider();
  if(configuration.provider==='google'&&import.meta.env.VITE_GOOGLE_MAPS_API_KEY)return new GoogleProvider();
  if(configuration.provider==='osm')return osm;
  if(configuration.provider==='mapbox'&&import.meta.env.VITE_MAPBOX_ACCESS_TOKEN)return mapbox;
  if(configuration.provider==='google'||configuration.provider==='mapbox')return navigator.onLine?osm:offline;
  return configuration.provider==='offline'?offline:osm;
}
