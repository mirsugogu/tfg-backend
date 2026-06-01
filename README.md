# Optima Backend API

Backend REST multi-tenant para gestión de citas, empleados, clientes, servicios y disponibilidad.
Trabajo de Fin de Grado — DAM.

**La aplicación también está desplegada en producción:**
[https://optima-aws.duckdns.org/login](https://optima-aws.duckdns.org/login)

Si hay cualquier problema ejecutándola en local, se puede acceder a esa URL directamente para probarla.

---

## Stack tecnológico

- Java 21 + Spring Boot 3.4.1
- Maven (wrapper incluido, no hace falta instalarlo aparte)
- MySQL 8
- Spring Security + JWT
- JPA / Hibernate
- Swagger / OpenAPI
- Frontend: React 19 + Vite + Tailwind CSS 4

---

## Requisitos previos

| Herramienta | ¿Obligatoria? | Para qué |
|-------------|---------------|----------|
| Java 21 (JDK) | Sí (sin Docker) | Compilar y ejecutar el backend |
| Docker Desktop | No, pero recomendado | Levantar MySQL + backend con un solo comando |
| Node.js (LTS) | Solo para el frontend | Instalar dependencias y arrancar Vite |

- **Java 21**: [Adoptium](https://adoptium.net/) o [Oracle](https://www.oracle.com/java/technologies/downloads/)
- **Docker Desktop**: [https://www.docker.com/products/docker-desktop/](https://www.docker.com/products/docker-desktop/)
- **Node.js**: [https://nodejs.org/](https://nodejs.org/) (versión LTS, incluye `npm`)

---

## Nota sobre seguridad (JWT)

La clave JWT tiene un valor por defecto en `application.properties` para que la aplicación funcione sin configurar nada extra. Sabemos que en un entorno real esto debería estar en una variable de entorno y no en el código fuente. En nuestra versión desplegada en AWS sí está externalizada en un `.env` que no se sube al repositorio. Aquí se deja en el código solo para facilitar la corrección.

---

## Opción 1: Con Docker (recomendado)

Levanta MySQL y la API juntos con un solo comando.

### 1. Crear el archivo `.env`

**Windows (PowerShell):**
```powershell
Copy-Item .env-example .env
```

**Linux / macOS:**
```bash
cp .env-example .env
```

El `.env-example` ya trae los valores necesarios (incluida la clave JWT), así que no hace falta modificar nada.

### 2. Levantar los contenedores

```bash
docker compose up --build
```

La primera vez tarda unos minutos porque descarga las imágenes y compila.

### 3. Comprobar que funciona

- API: [http://localhost:8080](http://localhost:8080)
- Swagger: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

### Resetear la base de datos

Si se necesita empezar de cero:

```bash
docker compose down -v
docker compose up --build
```

---

## Opción 2: Sin Docker

Hace falta MySQL 8 instalado en el equipo. Se usa MySQL Workbench para preparar la base de datos.

### 1. Preparar la base de datos con MySQL Workbench

1. Abrir MySQL Workbench y conectar al servidor local en `localhost:3306`
2. Abrir el archivo `docs/schema_v20.sql` (File → Open SQL Script...)
3. Ejecutar todo el script (Ctrl+Shift+Enter o el botón del rayo ⚡)

El script crea la base de datos, las tablas, el usuario `optima_user` y carga los datos de ejemplo. No hace falta ejecutar ningún otro comando SQL.

### 2. Arrancar el backend

**Windows (PowerShell):**
```powershell
.\mvnw.cmd spring-boot:run
```

**Linux / macOS:**
```bash
chmod +x mvnw
./mvnw spring-boot:run
```

La API queda en [http://localhost:8080](http://localhost:8080).

---

## Frontend

El frontend está en la carpeta `frontend-v2/`.

```bash
cd frontend-v2
npm install
npm run dev
```

Queda disponible en [http://localhost:5173](http://localhost:5173). Conecta automáticamente con el backend en `localhost:8080`.

---

## Datos de prueba

El script `docs/schema_v20.sql` inserta datos de ejemplo.

| Email | Contraseña | Rol |
|-------|------------|-----|
| admin@optima.com | 12345678 | Administrador |
| empleado@optima.com | 12345678 | Empleado |

---

## Documentación de la API

- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **Colección Postman**: `docs/optima-postman-collection-v4.json` — se puede importar en Postman para probar los endpoints.

---

## Estructura del proyecto

```
├── src/main/java/com/optima/api/   # Código del backend
│   ├── common/                     # Seguridad, config, filtros, excepciones
│   ├── appointment/                # Citas
│   ├── business/                   # Negocios y horarios
│   ├── catalog/                    # Servicios y categorías
│   ├── client/                     # Clientes
│   └── user/                       # Usuarios y membresías
├── docs/                           # Schema SQL, Postman
├── frontend-v2/                    # Frontend React
├── docker-compose.yml
└── pom.xml
```
