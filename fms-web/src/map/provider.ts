import L from 'leaflet';

export type MapConfiguration={provider:'google'|'osm'|'offline'|'mapbox';default_latitude:number;default_longitude:number;default_zoom:number;tile_url?:string;style_url?:string};
export type MapVehicle={name:string;latitude:number;longitude:number;status:string;stale:boolean};
export interface MapHandle { addVehicles(vehicles:MapVehicle[]):void; setVehiclesVisible(visible:boolean):void; destroy():void; }
export interface MapProvider { readonly name:string; mount(element:HTMLElement,configuration:MapConfiguration):Promise<MapHandle>; }

class LeafletRasterProvider implements MapProvider {
  constructor(public readonly name:string,private readonly tile:(c:MapConfiguration)=>string){}
  async mount(element:HTMLElement,c:MapConfiguration):Promise<MapHandle>{
    const map=L.map(element,{preferCanvas:true}).setView([c.default_latitude,c.default_longitude],c.default_zoom);
    L.tileLayer(this.tile(c),{maxZoom:20,attribution:'© OpenStreetMap contributors'}).addTo(map);
    const cluster=L.markerClusterGroup({chunkedLoading:true,maxClusterRadius:50});map.addLayer(cluster);
    return {addVehicles(vehicles){vehicles.forEach(v=>{const icon=L.divIcon({className:'vehicle-pin',html:`<span class="${v.stale?'stale':''}">▰</span>`,iconSize:[28,28]});L.marker([v.latitude,v.longitude],{icon,title:v.name}).bindPopup(`<b>${v.name}</b><br>${v.status}<br>${v.stale?'Location stale':'Live position'}`).addTo(cluster)})},setVehiclesVisible(visible){if(visible&&!map.hasLayer(cluster))map.addLayer(cluster);if(!visible&&map.hasLayer(cluster))map.removeLayer(cluster)},destroy(){map.remove()}};
  }
}

class GoogleProvider implements MapProvider {
  readonly name='Google Maps';
  async mount(element:HTMLElement,c:MapConfiguration):Promise<MapHandle>{
    const key=import.meta.env.VITE_GOOGLE_MAPS_API_KEY;
    await loadGoogle(key);
    const googleApi=(window as unknown as {google:{maps:{Map:new(e:HTMLElement,o:object)=>unknown;Marker:new(o:object)=>{setMap:(map:unknown|null)=>void}}}}).google;
    const map=new googleApi.maps.Map(element,{center:{lat:c.default_latitude,lng:c.default_longitude},zoom:c.default_zoom,mapTypeControl:false});
    const markers:{setMap:(map:unknown|null)=>void}[]=[];
    return {addVehicles(vehicles){vehicles.forEach(v=>markers.push(new googleApi.maps.Marker({map,position:{lat:v.latitude,lng:v.longitude},title:`${v.name} · ${v.status}`})))},setVehiclesVisible(visible){markers.forEach(marker=>marker.setMap(visible?map:null))},destroy(){markers.forEach(marker=>marker.setMap(null));element.replaceChildren()}};
  }
}

let googlePromise:Promise<void>|undefined;
function loadGoogle(key:string):Promise<void>{if(googlePromise)return googlePromise;googlePromise=new Promise((resolve,reject)=>{const script=document.createElement('script');script.src=`https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(key)}`;script.async=true;script.onload=()=>resolve();script.onerror=()=>reject(new Error('Google Maps could not be loaded'));document.head.appendChild(script)});return googlePromise}

const offline=new LeafletRasterProvider('Offline tiles',c=>c.tile_url||import.meta.env.VITE_OFFLINE_TILE_URL||'/tiles/{z}/{x}/{y}.png');
const osm=new LeafletRasterProvider('OpenStreetMap',()=>import.meta.env.VITE_OSM_TILE_URL||'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png');
const mapbox=new LeafletRasterProvider('Mapbox-compatible',c=>c.tile_url||'/tiles/{z}/{x}/{y}.png');

export function resolveProvider(c:MapConfiguration):MapProvider {
  if(c.provider==='google'&&import.meta.env.VITE_GOOGLE_MAPS_API_KEY)return new GoogleProvider();
  if(c.provider==='osm')return osm;
  if(c.provider==='mapbox'&&import.meta.env.VITE_MAPBOX_ACCESS_TOKEN)return mapbox;
  if(c.provider==='google'||c.provider==='mapbox')return navigator.onLine?osm:offline;
  return c.provider==='offline'?offline:osm;
}
