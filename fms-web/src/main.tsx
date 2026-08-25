import React, { Suspense, lazy, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { AppShell } from './shell/AppShell';
import { LoginScreen, isAuthenticated } from './security/LoginScreen';
import { ErrorBoundary } from './shell/ErrorBoundary';
import './styles.css';

const Overview = lazy(() => import('./pages/OverviewPage'));
const Plant = lazy(() => import('./pages/PlantPage'));
const MapPage = lazy(() => import('./pages/MapPage'));
const Vehicles = lazy(() => import('./pages/VehiclesPage'));
const Orders = lazy(() => import('./pages/OrdersPage'));
const Operations = lazy(() => import('./pages/OperationsPage'));
const Automation = lazy(() => import('./AutomationAdmin'));
const ControlCenter = lazy(() => import('./pages/ControlCenterPage'));
const Administration = lazy(() => import('./pages/AdministrationPage'));

const routes: Record<string, React.LazyExoticComponent<() => React.ReactElement>> = {
  overview: Overview, plant: Plant, map: MapPage, vehicles: Vehicles, orders: Orders,
  jobs: Operations, scheduling: Operations, dispatch: Operations, fueling: Automation,
  parking: Automation, charging: Automation, alerts: Operations, reports: Operations,
  'control-center': ControlCenter, administration: Administration
};

function currentRoute() { return location.hash.replace(/^#\/?/, '').split('/')[0] || 'overview'; }

function App() {
  const [authenticated, setAuthenticated] = useState(isAuthenticated());
  const [route, setRoute] = useState(currentRoute());
  useEffect(() => { const listener = () => setRoute(currentRoute()); addEventListener('hashchange', listener); return () => removeEventListener('hashchange', listener); }, []);
  if (!authenticated) return <LoginScreen onLogin={() => setAuthenticated(true)} />;
  const Page = routes[route] ?? Overview;
  return <AppShell route={route} onLogout={() => setAuthenticated(false)}>
    <ErrorBoundary><Suspense fallback={<div className="page-state">Loading operational workspace…</div>}><Page /></Suspense></ErrorBoundary>
  </AppShell>;
}

createRoot(document.getElementById('root')!).render(<React.StrictMode><App /></React.StrictMode>);
