import { useEffect, useState } from 'react';
import { api } from './api/client';

type Rule = { id:string; code:string; name:string; kind:'PARKING'|'FUELING'; scopeType:string; enabled:boolean; priority:number; version:number; thresholds:Record<string,number>; weights:Record<string,number> };
type Decision = { id:string; assetId:string; evaluatedAt:string; selectedAction:string; selectedResourceId?:string; eligible:boolean; jobCreated:boolean; blockingReasons:string[]; candidates:{resourceId:string;totalScore:number;eligible:boolean;rejectionReasons:string[]}[] };
type Reservation = { id:string; resourceType:string; resourceId:string; assetId:string; expiresAt:string };
type Alert = { id:string; code:string; severity:string; message:string; lastRaisedAt:string };

export default function AutomationAdmin(){
  const [rules,setRules]=useState<Rule[]>([]),[decisions,setDecisions]=useState<Decision[]>([]),[reservations,setReservations]=useState<Reservation[]>([]),[alerts,setAlerts]=useState<Alert[]>([]),[section,setSection]=useState('rules');
  const refresh=()=>Promise.all([
    api<Rule[]>('/api/automation/rules'),
    api<Decision[]>('/api/automation/decisions'),
    api<Reservation[]>('/api/automation/reservations'),
    api<Alert[]>('/api/automation/alerts')
  ]).then(([r,d,v,a])=>{setRules(r);setDecisions(d);setReservations(v);setAlerts(a)}).catch(console.error);
  useEffect(()=>{refresh();const timer=setInterval(refresh,10000);return()=>clearInterval(timer)},[]);
  return <section className="admin-page">
    <div className="admin-heading"><div><small>ADMINISTRATION / AUTOMATION</small><h1>Automatic parking & fueling</h1><p>Versioned rules, deterministic decisions, reservations and safety exceptions.</p></div><button className="primary compact" title="Creates a new inactive version that requires safety approval">+ NEW RULE VERSION</button></div>
    <nav className="admin-tabs">{['rules','decisions','reservations','alerts','simulation','overrides'].map(s=><button className={section===s?'active':''} onClick={()=>setSection(s)}>{s.toUpperCase()}</button>)}</nav>
    {section==='rules'&&<div className="rule-grid">{rules.map(r=><article className="rule-card"><header><span className={r.kind==='FUELING'?'fuel icon':'park icon'}>{r.kind==='FUELING'?'F':'P'}</span><div><b>{r.name}</b><small>{r.code} · VERSION {r.version}</small></div><i className={r.enabled?'on':'off'}>{r.enabled?'ACTIVE':'DISABLED'}</i></header><dl><dt>Scope <span title="The most specific active scope wins: asset, group, type, zone, terminal, global.">?</span></dt><dd>{r.scopeType}</dd><dt>Priority <span title="Higher priority resolves ties inside the same scope.">?</span></dt><dd>{r.priority}</dd>{Object.entries(r.thresholds).slice(0,4).map(([k,v])=><><dt>{k} <span title="Safety threshold. Changes require Administrator approval.">?</span></dt><dd>{v}</dd></>)}</dl><footer><button>SIMULATE</button><button>CREATE VERSION</button></footer></article>)}</div>}
    {section==='decisions'&&<DataTable heads={['TIME','ASSET','ACTION','DESTINATION','RESULT','BLOCKING REASONS']} rows={decisions.slice().reverse().map(d=>[new Date(d.evaluatedAt).toLocaleString(),d.assetId.slice(0,8),d.selectedAction,d.selectedResourceId??'—',d.jobCreated?'JOB CREATED':d.eligible?'ELIGIBLE':'BLOCKED',d.blockingReasons.join(', ')||'—'])}/>} 
    {section==='reservations'&&<DataTable heads={['RESOURCE TYPE','RESOURCE','ASSET','EXPIRES','STATUS']} rows={reservations.map(r=>[r.resourceType,r.resourceId,r.assetId.slice(0,8),new Date(r.expiresAt).toLocaleString(),'ACTIVE'])}/>} 
    {section==='alerts'&&<DataTable heads={['SEVERITY','CODE','MESSAGE','LAST RAISED']} rows={alerts.map(a=>[a.severity,a.code,a.message,new Date(a.lastRaisedAt).toLocaleString()])}/>} 
    {section==='simulation'&&<div className="sim-panel"><h2>Decision simulation</h2><p>Runs the production evaluator without creating a job or reservation. The response includes the input snapshot, matching rules, blocking reasons and every scoring component.</p><label>Automation asset ID</label><input placeholder="Select an automation asset"/><button className="primary compact">RUN SAFE SIMULATION</button></div>}
    {section==='overrides'&&<div className="sim-panel"><h2>Manual overrides</h2><p>Dispatcher overrides, exceptional dispatch approvals and reservation releases are authorization-controlled and audit logged.</p><div className="notice">Safety threshold overrides require Administrator role and explicit confirmation.</div></div>}
  </section>
}
function DataTable({heads,rows}:{heads:string[];rows:(string|number)[][]}){return <div className="data-table"><table><thead><tr>{heads.map(h=><th>{h}</th>)}</tr></thead><tbody>{rows.map((row,i)=><tr key={i}>{row.map(v=><td>{v}</td>)}</tr>)}</tbody></table>{rows.length===0&&<p className="empty">No records yet. Simulator and scheduled reconciliation decisions appear here.</p>}</div>}
