import { FormEvent, useState } from 'react';
import { api, saveLocalCredentials } from '../api/client';
export const isAuthenticated=()=>Boolean(sessionStorage.getItem('dpwfms.localBasic'));
export function LoginScreen({onLogin}:{onLogin:()=>void}){
 const[user,setUser]=useState(''),[password,setPassword]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false);
 async function submit(event:FormEvent){event.preventDefault();setBusy(true);setError('');saveLocalCredentials(user,password);try{await api('/api/workspace/overview');onLogin();}catch(e){sessionStorage.clear();setError(e instanceof Error?e.message:'Sign in failed');}finally{setBusy(false)}}
 return <main className="login"><form onSubmit={submit}><div className="logo-mark">DP</div><h1>DPW FMS</h1><p>Fleet Management System</p><label>Username<input autoFocus autoComplete="username" value={user} onChange={e=>setUser(e.target.value)} required/></label><label>Password<input type="password" autoComplete="current-password" value={password} onChange={e=>setPassword(e.target.value)} required/></label>{error&&<div className="error" role="alert">{error}</div>}<button className="primary" disabled={busy}>{busy?'Connecting…':'Sign in'}</button><small>Local authentication is for the development profile only.</small></form></main>
}
