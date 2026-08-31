import L from 'leaflet';

export type MapConfiguration={provider:'google'|'osm'|'offline'|'mapbox';default_latitude:number;default_longitude:number;default_zoom:number;tile_url?:string;style_url?:string};
export type MapVehicle={name:string;latitude:number;longitude:number;status:string;stale:boolean};
export interface MapHandle { setVehicles(vehicles:MapVehicle[]):void; setVehiclesVisible(visible:boolean):void; invalidateSize():void; destroy():void; }
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
    const vehicleLayer=L.layerGroup().addTo(map);
    let visible=true;

    const handle:MapHandle={
      setVehicles(vehicles){
        vehicleLayer.clearLayers();
        vehicles.forEach(vehicle=>{
          const color=vehicle.stale?'#ff9e22':'#38d56b';
          L.circleMarker([vehicle.latitude,vehicle.longitude],{
            radius:8,color:'#ffffff',weight:2,fillColor:color,fillOpacity:1
          }).bindTooltip(vehicle.name,{direction:'top'})
            .bindPopup(`<b>${escapeHtml(vehicle.name)}</b><br>${escapeHtml(vehicle.status)}<br>${vehicle.stale?'Location stale':'Live position'}`)
            .addTo(vehicleLayer);
        });
        if(visible&&vehicles.length>0){
          const bounds=L.latLngBounds(vehicles.map(vehicle=>[vehicle.latitude,vehicle.longitude]));
          if(bounds.isValid())map.fitBounds(bounds.pad(.15),{maxZoom:configuration.default_zoom});
        }
      },
      setVehiclesVisible(nextVisible){
        visible=nextVisible;
        if(nextVisible&&!map.hasLayer(vehicleLayer))vehicleLayer.addTo(map);
        if(!nextVisible&&map.hasLayer(vehicleLayer))map.removeLayer(vehicleLayer);
      },
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
