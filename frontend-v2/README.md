# Optima — Frontend v2

SPA en React 19 + Vite 8 + React Router 7 + Tailwind CSS 4 que consume el
backend REST de Optima (Spring Boot, ver [`../CLAUDE.md`](../CLAUDE.md)).

> El detalle del proyecto (estructura, bundle UI/UX, hallazgos del
> backend que el frontend esquiva, trabajo posterior al backlog del
> tester con drag-and-drop, ausencias, revalidación de sesión, etc.)
> vive en **[`PROGRESS.md`](./PROGRESS.md)** — ese es el documento de
> contexto canónico del frontend.

## Arranque

```powershell
npm install
npm run dev      # dev server http://127.0.0.1:5173 (Vite, IPv4 forzada)
npm run build    # build de producción a dist/
npm run preview  # sirve dist/ en local para verificar
```

Necesita el backend en `http://localhost:8080`. Levantarlo desde la raíz
del repo con `docker compose up -d` y esperar ~50 s.

## Login demo

`admin@optima.com` / `12345678` — el usuario `admin` tiene 2 memberships
en el seed, así que dispara el flujo de login en 2 pasos (identity → select
business). Cualquiera de los otros 6 usuarios del seed entra directo con
tenant token. Password común: `12345678`.
