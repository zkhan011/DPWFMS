import { FormEvent, useEffect, useState } from 'react';
import { api } from '../api/client';
import { Empty, Failure, Loading, PageHeader, Status } from '../components/ui';

type Integration={id:string;integration_code:string;integration_type:string;enabled:boolean;endpoint?:string;port?:number;tls_enabled:boolean;health_status:string};
type User={id:string;username:string;display_name:string;enabled:boolean;service_account:boolean;roles:string;plants:string;last_login_at?:string};
type Role={id:string;name:string;description:string;protected_role:boolean};
type Plant={id:string;name:string};
type MapConfig={id:string;provider:string;default_latitude:number;default_longitude:number;default_zoom:number;tile_url?:string;style_url?:string;visible_layers:unknown;version:number};
type Tab='integrations'|'users'|'maps';

export default function AdministrationPage(){
  const[tab,setTab]=useState<Tab>('users'),[loading,setLoading]=useState(true),[error,setError]=useState('');
  const[integrations,setIntegrations]=useState<Integration[]>([]),[users,setUsers]=useState<User[]>([]),[roles,setRoles]=useState<Role[]>([]),[plants,setPlants]=useState<Plant[]>([]),[map,setMap]=useState<MapConfig>();
  async function load(){
    setLoading(true);setError('');
    const results=await Promise.allSettled([
      api<Integration[]>('/api/workspace/integrations'),api<User[]>('/api/admin/users'),
      api<Role[]>('/api/admin/roles'),api<Plant[]>('/api/workspace/plants'),
      api<MapConfig>('/api/workspace/map-configuration')
    ]);
    if(results[0].status==='fulfilled')setIntegrations(results[0].value);
    if(results[1].status==='fulfilled')setUsers(results[1].value);
    if(results[2].status==='fulfilled')setRoles(results[2].value);
    if(results[3].status==='fulfilled')setPlants(results[3].value);
    if(results[4].status==='fulfilled')setMap(results[4].value);
    const failures=results.filter(result=>result.status==='rejected');
    if(failures.length===results.length)setError('Your account cannot access administration data. Sign in as an administrator.');
    setLoading(false);
  }
  useEffect(()=>{load()},[]);
  if(loading)return <Loading/>;if(error)return <Failure message={error}/>;
  return <><PageHeader eyebrow="GOVERNANCE" title="Administration" description="Manage users, roles, plants, maps, integrations and master data."/>
    <nav className="admin-tabs" aria-label="Administration sections"><button className={tab==='integrations'?'active':''} onClick={()=>setTab('integrations')}>Integrations</button><button className={tab==='users'?'active':''} onClick={()=>setTab('users')}>Users & access</button><button className={tab==='maps'?'active':''} onClick={()=>setTab('maps')}>Map provider</button></nav>
    {tab==='integrations'&&<IntegrationTable rows={integrations}/>}
    {tab==='users'&&(roles.length?<UserAdministration users={users} roles={roles} plants={plants} onCreated={load}/>:<Failure message="User administration requires user.read and role.read permissions."/>)}
    {tab==='maps'&&(map?<MapAdministration configuration={map} onSaved={load}/>:<Failure message="Map administration requires map.read permission."/>)} </>;
}

function IntegrationTable({rows}:{rows:Integration[]}){return <><section className="panel table-panel">{rows.length?<table><thead><tr><th>Integration</th><th>Type</th><th>Enabled</th><th>Endpoint</th><th>TLS</th><th>Health</th></tr></thead><tbody>{rows.map(i=><tr key={i.id}><td><b>{i.integration_code}</b></td><td>{i.integration_type}</td><td><Status value={i.enabled?'ENABLED':'DISABLED'}/></td><td>{i.endpoint?`${i.endpoint}${i.port?`:${i.port}`:''}`:'Environment configuration'}</td><td>{i.tls_enabled?'Required':'Disabled'}</td><td><Status value={i.health_status}/></td></tr>)}</tbody></table>:<Empty message="No integration adapters are configured."/>}</section><div className="warning panel"><b>Secrets are write-only</b><p>Authentication references are masked and never returned to this browser.</p></div></>}

function UserAdministration({users,roles,plants,onCreated}:{users:User[];roles:Role[];plants:Plant[];onCreated:()=>Promise<void>}){
 const[open,setOpen]=useState(false),[busy,setBusy]=useState(false),[message,setMessage]=useState('');
 async function create(event:FormEvent<HTMLFormElement>){event.preventDefault();setBusy(true);setMessage('');const form=new FormData(event.currentTarget);try{await api('/api/admin/users',{method:'POST',body:JSON.stringify({username:String(form.get('username')).trim(),displayName:String(form.get('displayName')).trim(),password:form.get('password'),serviceAccount:form.get('serviceAccount')==='on',roleIds:form.getAll('roleIds'),plantIds:form.getAll('plantIds')})});setOpen(false);await onCreated()}catch(e){setMessage(e instanceof Error?e.message:'User creation failed')}finally{setBusy(false)}}
 return <div className="admin-two-column"><section className="panel table-panel"><div className="panel-title"><h2>Application users</h2><button className="primary" onClick={()=>setOpen(true)}>+ Create user</button></div>{users.length?<table><thead><tr><th>User</th><th>Roles</th><th>Plants</th><th>Type</th><th>Status</th><th>Last login</th></tr></thead><tbody>{users.map(u=><tr key={u.id}><td><b>{u.display_name}</b><small>{u.username}</small></td><td>{u.roles||'No role'}</td><td>{u.plants||'All / unassigned'}</td><td>{u.service_account?'Service':'Interactive'}</td><td><Status value={u.enabled?'ENABLED':'DISABLED'}/></td><td>{u.last_login_at?new Date(u.last_login_at).toLocaleString():'Never'}</td></tr>)}</tbody></table>:<Empty message="No database users have been created. The environment bootstrap account remains available."/>}</section>{open&&<div className="modal-backdrop" role="presentation"><form className="modal" onSubmit={create}><h2>Create DPW FMS user</h2><p>Passwords are hashed by the backend and are never returned.</p><label>Username<input name="username" minLength={3} maxLength={160} required autoFocus/></label><label>Display name<input name="displayName" maxLength={160} required/></label><label>Initial password<input name="password" type="password" minLength={12} maxLength={200} required autoComplete="new-password"/></label><label>Roles<select name="roleIds" multiple required size={Math.min(8,roles.length)}>{roles.map(r=><option key={r.id} value={r.id}>{r.name}{r.protected_role?' · protected':''}</option>)}</select></label><label>Plant access<select name="plantIds" multiple size={Math.min(6,plants.length)}>{plants.map(p=><option key={p.id} value={p.id}>{p.name}</option>)}</select></label><label className="check"><input name="serviceAccount" type="checkbox"/> Non-interactive service account</label>{message&&<div className="error" role="alert">{message}</div>}<footer><button type="button" onClick={()=>setOpen(false)}>Cancel</button><button className="primary" disabled={busy}>{busy?'Creating…':'Create user'}</button></footer></form></div>}</div>;
}

function MapAdministration({configuration,onSaved}:{configuration:MapConfig;onSaved:()=>Promise<void>}){
 const[busy,setBusy]=useState(false),[message,setMessage]=useState('');const layers=normalizeLayers(configuration.visible_layers);
 async function save(event:FormEvent<HTMLFormElement>){event.preventDefault();setBusy(true);setMessage('');const form=new FormData(event.currentTarget);try{await api(`/api/workspace/map-configuration/${configuration.id}`,{method:'PUT',body:JSON.stringify({provider:form.get('provider'),latitude:Number(form.get('latitude')),longitude:Number(form.get('longitude')),zoom:Number(form.get('zoom')),tileUrl:form.get('tileUrl')||null,styleUrl:form.get('styleUrl')||null,secretReference:form.get('secretReference')||null,visibleLayers:form.getAll('visibleLayers')})});setMessage('Map configuration saved. Reopen the Map view to apply it.');await onSaved()}catch(e){setMessage(e instanceof Error?e.message:'Map configuration failed')}finally{setBusy(false)}}
 async function test(){setBusy(true);try{const result=await api<{status:string;message:string}>(`/api/workspace/map-configuration/${configuration.id}/test`,{method:'POST'});setMessage(`${result.status}: ${result.message}`)}catch(e){setMessage(e instanceof Error?e.message:'Connectivity test failed')}finally{setBusy(false)}}
 return <form className="panel config-form" onSubmit={save}><div className="panel-title"><div><h2>Operational map provider</h2><p>Provider credentials remain masked after saving.</p></div><Status value={configuration.provider.toUpperCase()}/></div><div className="form-grid"><label>Provider<select name="provider" defaultValue={configuration.provider}><option value="offline">Offline XYZ</option><option value="osm">OpenStreetMap</option><option value="google">Google Maps</option><option value="mapbox">Mapbox-compatible</option></select></label><label>Default latitude<input name="latitude" type="number" step="0.000001" min="-90" max="90" defaultValue={configuration.default_latitude} required/></label><label>Default longitude<input name="longitude" type="number" step="0.000001" min="-180" max="180" defaultValue={configuration.default_longitude} required/></label><label>Default zoom<input name="zoom" type="number" min="1" max="22" defaultValue={configuration.default_zoom} required/></label><label>XYZ tile URL<input name="tileUrl" defaultValue={configuration.tile_url??''} placeholder="/tiles/{z}/{x}/{y}.png"/></label><label>Vector style URL<input name="styleUrl" defaultValue={configuration.style_url??''} placeholder="/maps/style.json"/></label><label>Secret-manager reference<input name="secretReference" type="password" placeholder="Leave blank to preserve existing reference" autoComplete="new-password"/></label></div><fieldset><legend>Visible operational layers</legend>{['plants','vehicles','parking','fueling','charging','alerts','geofences','routes','trails'].map(layer=><label className="check" key={layer}><input type="checkbox" name="visibleLayers" value={layer} defaultChecked={layers.includes(layer)}/>{layer}</label>)}</fieldset>{message&&<div className="notice" role="status">{message}</div>}<footer className="form-actions"><button type="button" onClick={test} disabled={busy}>Test configuration</button><button className="primary" disabled={busy}>Save map settings</button></footer></form>;
}
function normalizeLayers(value:unknown):string[]{if(Array.isArray(value))return value.map(String);if(typeof value==='string'){try{return JSON.parse(value)}catch{return[]}}if(value&&typeof value==='object'&&'value'in value)return normalizeLayers((value as {value:unknown}).value);return[]}
