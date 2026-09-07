export type ApiState<T> = {data?:T;loading:boolean;error?:string};
const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');

export function authHeader(): Record<string,string> {
  const token = sessionStorage.getItem('dpwfms.localBasic');
  return token ? {Authorization:`Basic ${token}`} : {};
}
export async function api<T>(path:string, init:RequestInit={}):Promise<T>{
  const response=await fetch(`${baseUrl}${path}`,{...init,headers:{'Content-Type':'application/json',...authHeader(),...(init.headers??{})}});
  if(response.status===401){sessionStorage.removeItem('dpwfms.localBasic');location.reload();throw new Error('Authentication required');}
  if(!response.ok){const text=await response.text();let detail=text;try{detail=JSON.parse(text).detail??text}catch{detail=text}throw new Error(detail||`Request failed (${response.status})`)}
  return response.status===204?undefined as T:response.json();
}
export async function subscribeSse(path:string,onTelemetry:()=>void,signal:AbortSignal):Promise<void>{
  const response=await fetch(`${baseUrl}${path}`,{headers:{...authHeader(),Accept:'text/event-stream'},signal});
  if(!response.ok||!response.body)throw new Error(`Live telemetry unavailable (${response.status})`);
  const reader=response.body.getReader(),decoder=new TextDecoder();let buffer='';
  while(!signal.aborted){const{done,value}=await reader.read();if(done)return;buffer+=decoder.decode(value,{stream:true});const events=buffer.split('\n\n');buffer=events.pop()??'';events.filter(event=>event.includes('event:telemetry')).forEach(()=>onTelemetry())}
}
export function saveLocalCredentials(username:string,password:string){sessionStorage.setItem('dpwfms.localBasic',btoa(`${username}:${password}`));sessionStorage.setItem('dpwfms.user',username);}
export function logout(){sessionStorage.clear();}
