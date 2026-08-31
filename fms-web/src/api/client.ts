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
export function saveLocalCredentials(username:string,password:string){sessionStorage.setItem('dpwfms.localBasic',btoa(`${username}:${password}`));sessionStorage.setItem('dpwfms.user',username);}
export function logout(){sessionStorage.clear();}
