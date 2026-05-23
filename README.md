# Optima — TFG DAM

SaaS multi-tenant de gestión de citas para pequeños negocios (peluquerías,
clínicas, talleres…). Backend REST en Java 21 / Spring Boot 3.4 + MySQL 8 +
frontend SPA en React 19 + Vite 8.

> Proyecto de TFG (Desarrollo de Aplicaciones Multiplataforma) de un equipo
> de 3 personas. NO se despliega a producción.

## Documentación

- **Backend** (arquitectura, entidades, decisiones de diseño):
  [`CLAUDE.md`](./CLAUDE.md) — o su gemelo para otras IAs,
  [`AGENTS.md`](./AGENTS.md).
- **Frontend** (estructura, bundle UI/UX, snapshot vigente con §11 del
  trabajo posterior al backlog): [`frontend-v2/PROGRESS.md`](./frontend-v2/PROGRESS.md).
- **Backlog del tester** (los 13 puntos + el trabajo posterior):
  [`docs/BACKLOG.md`](./docs/BACKLOG.md).
- **Contrato HTTP** (cobertura al 100% de los 17 controllers, lista para
  Postman / Newman): [`docs/optima-postman-collection-v4.json`](./docs/optima-postman-collection-v4.json).
- **Diagramas** (prosa de la memoria del TFG asociada al FigJam):
  [`docs/DIAGRAMAS.md`](./docs/DIAGRAMAS.md).
- **Auditorías históricas** (pre-frontend y revisión quirúrgica del
  2026-05-21):  [`docs/AUDIT_2026-05-17.md`](./docs/AUDIT_2026-05-17.md),
  [`docs/REVISION_QUIRURGICA.md`](./docs/REVISION_QUIRURGICA.md) y
  [`docs/audit/`](./docs/audit).

## Arranque rápido

```powershell
# Backend (MySQL + API) en Docker
docker compose up -d

# Frontend
cd frontend-v2
npm install
npm run dev    # http://127.0.0.1:5173
```

Credenciales demo (seed): `admin@optima.com` / `12345678`. La password es
`12345678` para los 7 usuarios del seed.
