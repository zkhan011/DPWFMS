import { ReactNode } from 'react';
export function PageHeader({eyebrow,title,description,actions}:{eyebrow:string;title:string;description:string;actions?:ReactNode}){return <div className="page-header"><div><small>{eyebrow}</small><h1>{title}</h1><p>{description}</p></div>{actions&&<div className="actions">{actions}</div>}</div>}
export function Kpi({label,value,tone='blue',icon,detail}:{label:string;value:ReactNode;tone?:string;icon:string;detail?:string}){return <article className="kpi"><i className={tone}>{icon}</i><div><small>{label}</small><strong>{value}</strong>{detail&&<span>{detail}</span>}</div></article>}
export function Status({value}:{value:string}){const key=value.toLowerCase().replaceAll('_','-');return <span className={`status ${key}`}>● {value.replaceAll('_',' ')}</span>}
export function Empty({message}:{message:string}){return <div className="empty"><b>No records</b><span>{message}</span></div>}
export function Loading(){return <div className="page-state">Loading live operational data…</div>}
export function Failure({message}:{message:string}){return <div className="page-state error" role="alert">{message}</div>}
