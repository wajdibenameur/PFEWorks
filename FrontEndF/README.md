# FrontEndF Audit And Integration Plan

## Objective

Build a new frontend shell in `FrontEndF` by reusing:

- the existing business logic from `frontend`
- the best monitoring-oriented visual ideas from `Angular/default` or `Angular/material`
- the simple admin management structure from `velson_template`

This avoids rewriting the monitoring domain from scratch.

## Existing Project Review

### Backend

The main backend already supports the product scope very well:

- multi-source monitoring
- Zabbix integration
- SNMP integration
- ZKBio integration
- camera inventory
- dashboard aggregation
- ticket management
- WebSocket publishing
- security and role-based access

Relevant backend areas:

- `Backend/src/main/java/tn/iteam/controller`
- `Backend/src/main/java/tn/iteam/service`
- `Backend/src/main/java/tn/iteam/integration`
- `Backend/src/main/java/tn/iteam/websocket`

### Auth Service

The authentication service is already separated and focused:

- login
- register
- refresh token
- Keycloak integration

Relevant area:

- `auth-service/src/main/java/tn/iteam/authservice`

### Current Frontend

The current Angular frontend is already structured in a good way:

- `core`
- `features`
- `layout`
- `shared`

It already contains real monitoring pages and not just placeholders:

- `dashboard`
- `monitoring/zabbix`
- `monitoring/snmp`
- `monitoring/camera`
- `monitoring/zkbio`
- `tickets/list`
- `tickets/add`
- `tickets/tracking`

Important existing routes:

- `frontend/src/app/app.routes.ts`

Important existing monitoring logic:

- `frontend/src/app/features/monitoring/data/monitoring-api.service.ts`
- `frontend/src/app/features/monitoring/data/monitoring-realtime.service.ts`
- `frontend/src/app/features/monitoring/state/monitoring.store.ts`
- `frontend/src/app/core/realtime/stomp-client.service.ts`

Important shared UI already built:

- `frontend/src/app/shared/ui/global-kpi-strip`
- `frontend/src/app/shared/ui/source-health-panel`
- `frontend/src/app/shared/ui/alert-summary-panel`
- `frontend/src/app/shared/ui/asset-inventory-table`
- `frontend/src/app/shared/ui/collection-control-bar`

## Best Template Choice

### Monitoring UI Base

Use `Angular/material` or `Angular/default` as the visual base for:

- dashboard cards
- graphs
- KPI blocks
- tables
- filter bars
- monitoring widgets

Why:

- these templates already include dense dashboard composition
- they are much closer to a monitoring cockpit than the current plain Angular pages
- they support charts, maps, summary cards, and operational tables

Reference pages:

- `Angular/default/src/app/pages/dashboards/dashboard/dashboard.component.html`
- `Angular/default/src/app/pages/dashboards/analytics/analytics.component.html`

### Admin UI Base

Use `velson_template` only for the admin area structure:

- users page
- admin menu shape
- backoffice table style

Reference files:

- `velson_template/src/app/pages/users/user-list/user-list.component.html`
- `velson_template/src/app/core/menu-models/adminMenu.ts`

Do not use `velson_template` as the main monitoring dashboard base because its admin dashboard is not implemented yet.

## What To Reuse In FrontEndF

### Keep From Current Frontend

These parts should be preserved almost as-is:

- route structure
- auth guard and role guard
- API services
- STOMP/WebSocket logic
- models
- state stores
- business terminology

Copy first:

- `frontend/src/app/core`
- `frontend/src/app/features/monitoring/data`
- `frontend/src/app/features/monitoring/state`
- `frontend/src/app/features/tickets/data`
- `frontend/src/app/core/models`

### Redesign Visually

These pages should be visually rebuilt in `FrontEndF`:

- dashboard
- monitoring zabbix page
- monitoring snmp page
- monitoring camera page
- monitoring zkbio page
- ticket list
- users
- admin

## Exact Mapping

### 1. Global Dashboard

Current page:

- `frontend/src/app/features/monitoring/ui/monitoring-dashboard-page.component.html`

Current strengths:

- already connected to monitoring facade
- already exposes KPI, source health, alerts, assets, coverage

Template inspiration:

- `Angular/default/src/app/pages/dashboards/dashboard/dashboard.component.html`
- `Angular/default/src/app/pages/dashboards/analytics/analytics.component.html`

What to bring into `FrontEndF`:

- top summary row with 4 to 6 KPI cards
- larger central trend chart area
- source distribution panel
- alert severity summary cards
- operational inventory table

Recommended new route target:

- `/dashboard`

### 2. Zabbix Workspace

Current page:

- `frontend/src/app/features/monitoring/ui/monitoring-zabbix-page.component.html`

Current strengths:

- already very rich in business content
- host list
- details pane
- KPI groups
- incidents
- prediction/anomaly information

Template inspiration:

- dashboard card spacing from `Angular/default`
- analytics summary composition from `Angular/default`

What to improve in `FrontEndF`:

- convert current text-heavy layout into visual KPI cards
- add compact metric widgets for CPU, RAM, ping, disk, network
- add stronger alert coloring
- keep the current host drill-down logic

Recommended new route target:

- `/monitoring/zabbix`

### 3. SNMP Workspace

Current page:

- `frontend/src/app/features/monitoring/ui/monitoring-snmp-page.component.html`

What to reuse:

- current data fetching
- current source-specific models

What to redesign:

- align with same workspace pattern as Zabbix
- emphasize device health, traffic, uptime, and interface metrics

Recommended route:

- `/monitoring/snmp`

### 4. Camera Workspace

Current page:

- `frontend/src/app/features/monitoring/ui/monitoring-camera-page.component.html`

What to redesign:

- camera inventory as cards + table
- online/offline status badges
- stream/health overview blocks

Recommended route:

- `/monitoring/camera`

### 5. ZKBio Workspace

Current page:

- `frontend/src/app/features/monitoring/ui/monitoring-zkbio-page.component.html`

What to redesign:

- attendance and access metrics
- device connectivity
- synchronization problems
- recent anomalies table

Recommended route:

- `/monitoring/zkbio`

### 6. Tickets

Current pages:

- `frontend/src/app/features/tickets/ui/ticket-list-page.component.html`
- `frontend/src/app/features/tickets/ui/ticket-add-page.component.html`
- `frontend/src/app/features/tickets/ui/ticket-tracking-page.component.html`

Template inspiration:

- card and table style from `velson_template` user list

What to bring into `FrontEndF`:

- denser header area
- clearer status badges
- search + filters in one toolbar
- side detail panel or modal for ticket preview

Recommended routes:

- `/tickets/list`
- `/tickets/add`
- `/tickets/tracking`

### 7. Users

Current route:

- `/users`

Current state:

- placeholder page in current frontend

Best template source:

- `velson_template/src/app/pages/users/user-list/user-list.component.html`

What to implement in `FrontEndF`:

- users table
- role filter
- status filter
- activate/deactivate actions
- edit role action
- connected profile count summary

Recommended route:

- `/users`

### 8. Admin Panel

Current route:

- `/admin`

Current state:

- placeholder page in current frontend

Template source:

- `velson_template` menu and table-oriented admin style

What to implement in `FrontEndF`:

- role management
- source activation/deactivation
- provider/source configuration
- system health summary
- audit actions block

Recommended route:

- `/admin`

## FrontEndF Recommended Structure

Use this structure:

- `FrontEndF/src/app/core`
- `FrontEndF/src/app/features/auth`
- `FrontEndF/src/app/features/monitoring`
- `FrontEndF/src/app/features/tickets`
- `FrontEndF/src/app/features/users`
- `FrontEndF/src/app/features/admin`
- `FrontEndF/src/app/layout`
- `FrontEndF/src/app/shared`

### Shared Components To Build First

- `kpi-card`
- `metric-trend-card`
- `status-badge`
- `filter-toolbar`
- `workspace-hero`
- `source-summary-grid`
- `incident-table`
- `host-detail-panel`
- `admin-stat-card`

## Recommended Integration Order

1. Initialize `FrontEndF` as a new Angular app.
2. Copy `core/models`, auth utilities, guards, and API configuration from the current `frontend`.
3. Copy monitoring services and state stores.
4. Rebuild the shell layout with sidebar + navbar + content area.
5. Rebuild `/dashboard` first using the current business data and `Angular/default` visual composition.
6. Rebuild `/monitoring/zabbix` second because it is your richest monitoring page.
7. Rebuild `/users` and `/admin` using `velson_template` as the admin visual basis.
8. Rebuild ticket pages after the monitoring shell is stable.

## Final Recommendation

Do not migrate everything from templates.

Use this rule:

- business logic from your current `frontend`
- dashboard composition from `Angular/default` or `Angular/material`
- admin table style from `velson_template`

This is the most efficient path because your project already has the hard part:

- monitoring domain
- backend contracts
- realtime communication
- routes
- security roles

What is still missing is mostly the visual refactor for dashboard, users, and admin.
