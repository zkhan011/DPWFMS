import L from 'leaflet';

export type MapConfiguration={provider:'google'|'osm'|'offline'|'mapbox';default_latitude:number;default_longitude:number;default_zoom:number;tile_url?:string;style_url?:string};
export type MapVehicle={name:string;latitude:number;longitude:number;status:string;stale:boolean;heading?:number;energyPercent?:number;energySource?:string;availability?:string;onSelect?:()=>void};
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
    const statusColor=(status:string,stale:boolean)=>stale?'#d68100':(['FAULT','CRITICAL'].includes(status)?'#d9253f':['OFFLINE','OUT_OF_SERVICE'].includes(status)?'#7a8792':['IDLE','PARKED','QUEUED'].includes(status)?'#e59a19':['ASSIGNED','WORKING','CHARGING','EN_ROUTE'].includes(status)?'#1684d8':'#2aa866');
    const render=()=>{vehicleLayer.clearLayers();const size=Math.max(.00008,4/Math.pow(2,map.getZoom()));const groups=new Map<string,MapVehicle[]>();currentVehicles.forEach(v=>{const key=`${Math.round(v.latitude/size)}:${Math.round(v.longitude/size)}`;groups.set(key,[...(groups.get(key)||[]),v])});groups.forEach(group=>{if(group.length>1){const lat=group.reduce((n,v)=>n+v.latitude,0)/group.length,lon=group.reduce((n,v)=>n+v.longitude,0)/group.length;L.marker([lat,lon],{icon:L.divIcon({className:'asset-cluster',html:`<span>${group.length}</span>`,iconSize:[34,34]})}).bindTooltip(`${group.length} assets`).on('click',()=>map.setZoomAround([lat,lon],map.getZoom()+2)).addTo(vehicleLayer)}else{const v=group[0],color=statusColor(v.status,v.stale);L.marker([v.latitude,v.longitude],{icon:L.divIcon({className:'asset-marker',html:`<span style="--marker:${color};transform:rotate(${v.heading||0}deg)">▲</span>`,iconSize:[28,28]})}).bindTooltip(v.name,{direction:'top'}).on('click',()=>v.onSelect?.()).addTo(vehicleLayer)}})};map.on('zoomend',render);

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

const offline=new LeafletRasterProvider('Offline tiles',configuration=>configuration.tile_url||import.meta.env.VITE_OFFLINE_TILE_URL||'/tiles/{z}/{x}/{y}.png');
const osm=new LeafletRasterProvider('OpenStreetMap',configuration=>configuration.tile_url||import.meta.env.VITE_OSM_TILE_URL||'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png');
const mapbox=new LeafletRasterProvider('Mapbox-compatible',configuration=>configuration.tile_url||'/tiles/{z}/{x}/{y}.png');

export function resolveProvider(configuration:MapConfiguration):MapProvider {
  if(configuration.provider==='google'&&import.meta.env.VITE_GOOGLE_MAPS_API_KEY)return new GoogleProvider();
  if(configuration.provider==='osm')return osm;
  if(configuration.provider==='mapbox'&&import.meta.env.VITE_MAPBOX_ACCESS_TOKEN)return mapbox;
  if(configuration.provider==='google'||configuration.provider==='mapbox')return navigator.onLine?osm:offline;
  return configuration.provider==='offline'?offline:osm;
}
