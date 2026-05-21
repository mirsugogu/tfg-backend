# Diagramas del TFG Optima — prosa para la memoria escrita

Este documento acompaña a los 40 diagramas del FigJam de la defensa y reúne
la prosa académica que justifica cada uno. Está pensado para copiarse
directamente en la memoria del TFG, ajustando tono y referencias bibliográficas
según la sección final donde se integre.

**FigJam de la defensa:** https://www.figma.com/board/WAo58CBNcUHpDAHFBjFuJl

Cada apartado describe **qué muestra** el diagrama, **qué decisión
arquitectónica respalda** y **cómo encaja en el guion de la defensa**. La
numeración coincide con la del FigJam.

---

## 1. Visión general

El diagrama sitúa a Optima en su contexto operativo: un backend SaaS al que se
conectan los administradores y empleados de cada pequeño negocio (peluquerías,
clínicas, talleres) a través de un frontend SPA. Las flechas continuas
representan tráfico HTTP autenticado mediante JWT; las flechas punteadas marcan
las dependencias hacia servicios externos (Nominatim para la geocodificación
inversa de direcciones y Gmail SMTP para el envío transaccional de correos de
bienvenida y recuperación de contraseña). La vista omite deliberadamente
detalles técnicos internos para servir como apertura de la defensa.

La decisión que respalda es la separación estricta entre backend y frontend: el
TFG aborda la API REST mientras que el cliente web se trata como pieza
acompañante. Mostrar las integraciones externas desde el inicio anticipa dos
secciones de la memoria —`common/geocoding` y `common/mail`— ambas cableadas
como dependencias *best-effort* para no acoplar la lógica de negocio a su
disponibilidad.

## 2. Arquitectura por capas

Representa el recorrido típico de una petición HTTP a través de la pila de
Spring Boot: filtros de seguridad (`RateLimitFilter`, `JwtAuthenticationFilter`,
`TenantGuardFilter`), capa de controladores anotados con `@RestController` y
`@PreAuthorize`, capa de servicios transaccionales (`@Service`,
`@Transactional`), validadores stateless (`@Component`), repositorios JPA y,
finalmente, la base de datos MySQL 8. La verticalidad del diagrama subraya el
flujo unidireccional desde la entrada HTTP hasta la persistencia.

La decisión arquitectónica que respalda es la separación clara de
responsabilidades. Aunque el código se organiza por módulo de dominio
(*feature-based*) y no por capa, cada módulo respeta internamente el patrón
controller → service → repository, lo que minimiza acoplamiento horizontal
entre módulos y permite a tres personas trabajar en paralelo sin colisiones de
Git. La capa de validadores como `@Component` independiente del servicio
justifica el patrón canónico documentado en la memoria.

## 3. Modelo entidad-relación (17 tablas)

Diagrama ER completo del *schema v20*. Las 17 tablas se agrupan visualmente en
cuatro bloques temáticos: identidad y pertenencia (`users`, `roles`,
`memberships`, `password_resets`), configuración del tenant (`businesses`,
`taxes`, `service_categories`, `services`, `booths`, `business_hours`),
calendario operativo (`employee_schedules`, `employee_absences`,
`schedule_blocks`) y citas (`appointments`, `appointment_services`,
`appointment_statuses`, junto con `clients`). Se muestran las cardinalidades
1:N entre todas las entidades y los atributos clave de las tablas más
relevantes (PK, FK, UK).

La decisión que respalda es el patrón multi-tenant *shared database, shared
schema*: salvo los catálogos globales (`roles`, `appointment_statuses`), todas
las tablas tenant-scoped llevan una columna `id_business` que aísla los datos
de cada negocio sin necesidad de esquemas separados. Otra decisión visible es
el desacoplamiento identidad↔pertenencia introducido en *schema v16*: la tabla
`users` solo guarda la identidad global (email único), mientras que
`memberships` resuelve la pareja (persona, negocio, rol) y se referencia desde
los calendarios y las citas en lugar de `users`.

## 4. Multi-tenancy (shared DB, shared schema)

Detalle visual del mecanismo de aislamiento entre tenants. Un usuario
autenticado recibe un JWT que contiene el *claim* `businessId`. El
`TenantGuardFilter` compara ese claim con el `businessId` del path de la
petición; si no coinciden devuelve 403. Más abajo, la capa de servicio
materializa el aislamiento llamando siempre a métodos `findByIdAndBusinessId`
que se traducen en `WHERE id_business = ?` a nivel SQL.

La decisión que respalda es ubicar la validación cross-tenant en la capa de
servicio, no en la base de datos. La BD no impone que, por ejemplo, una cita y
su cliente pertenezcan al mismo negocio: esa coherencia se garantiza desde
Java. Esta elección simplifica el esquema (no se necesitan triggers ni vistas
particionadas) y permite optimizaciones futuras como cachear la pertenencia o
añadir un *Row-Level Security* si el proyecto evolucionara a producción.

## 5. Login en 2 pasos (1 vs N memberships)

Diagrama del flujo de autenticación introducido en *schema v16*. Tras
`POST /api/auth/token` con email y contraseña, `AuthService` busca al usuario
(email único global) y verifica la contraseña con BCrypt. Si el usuario tiene
exactamente una *membership* activa, devuelve directamente un *tenant token*
con el `businessId` embebido. Si tiene varias, devuelve un *identity token*
junto con la lista de negocios para que el frontend muestre un selector; el
usuario completa el flujo con `POST /api/auth/select-business/{id}`, que canjea
el identity token por uno *tenant*.

La decisión que respalda es soportar identidades que pertenecen a varios
negocios (p. ej. una odontóloga que trabaja en dos clínicas) sin obligar al
usuario a recordar a qué negocio se asocia cada credencial. El JWT distingue
los dos tipos de token por la presencia del claim `businessId`, lo que permite
al `TenantGuardFilter` rechazar accesos a `/api/businesses/{id}/...` desde un
identity token. El mismo mensaje 401 *"Credenciales incorrectas"* en todos los
fallos evita filtrar información a un atacante.

## 6. Registro de negocio + admin inicial

Flujo del auto-registro público (`POST /api/auth/register`, único endpoint
junto con `/token` y los catálogos globales que vive bajo `permitAll`).
Bean Validation rechaza payloads inválidos; a continuación se verifica que el
email del admin y el slug del negocio no existan ya en la BD; el
`GeocodingService` resuelve la dirección postal a latitud/longitud mediante
Nominatim. La transacción crea en una sola unidad atómica las tres entidades
(`User`, `Business`, `Membership` con rol ADMIN), confirma el commit y, ya
fuera del *happy path*, dispara un correo de bienvenida *best-effort* desde
`MailService`.

La decisión que respalda es prescindir de un panel de superadministración. El
alta de negocios es por auto-registro porque el TFG opta por simplicidad
funcional sobre control centralizado: cualquier dueño de negocio puede crear
su tenant sin intervención humana. Crear las tres entidades en una sola
transacción evita estados inconsistentes (un `Business` sin admin asociado,
por ejemplo) en caso de fallo intermedio.

## 7. Crear cita con validaciones

Diagrama detallado del flujo más complejo del proyecto:
`POST /api/businesses/{businessId}/appointments`. Tras superar Bean Validation
y `TenantGuardFilter`, el servicio recorre doce pasos dentro de una única
transacción: cargar y validar `Business`, `Client`, `Membership`, `Booth`
opcional y `BusinessService`s; validar que la hora de inicio respeta el
intervalo del negocio (`validateAppointmentInterval`); comprobar el horario
de apertura del negocio (`validateBusinessHours`); comprobar que la cita
encaja en el horario del empleado (`validateEmployeeSchedule`); detectar
solapes con otras citas del mismo empleado bajo pessimistic lock
(`validateNoOverlap`); excluir ausencias del empleado
(`validateNoEmployeeAbsence`); detectar solapes de la cabina si hay
(`validateNoBoothOverlap`); comprobar bloqueos globales / por empleado / por
cabina (`validateNoScheduleBlock`); congelar el precio y el porcentaje de
impuesto en `applied_price` y `applied_tax_percentage`; finalmente persistir
`Appointment` y los `BookedService`s. Cada validación tiene su código HTTP de salida: 400 para
datos inválidos, 409 para conflictos de calendario.

La decisión que respalda es delegar la lógica de validación a un componente
`AppointmentValidator` stateless que falla rápido lanzando
`ResponseStatusException`, en lugar de devolver booleans y dejar al servicio
construir las excepciones. Otra decisión visible es el *frozen price pattern*:
las citas históricas conservan el precio y el impuesto que se aplicaron en su
día aunque más tarde cambien los valores del servicio o el porcentaje del
impuesto, lo que garantiza trazabilidad económica.

## 8. Cálculo de disponibilidad (algoritmo en 9 pasos)

Recorrido por los nueve pasos de `AvailabilityService.getAvailability`. El
algoritmo recibe `date`, `serviceIds` y opcionalmente `employeeId` y
`boothId`; tras validar el negocio y resolver la duración total sumando los
servicios, comprueba el horario del día (si está cerrado devuelve una lista
vacía), aplica los `schedule_blocks` aplicables (global → lista vacía;
por empleado o cabina → exclusión), resuelve los empleados y las cabinas
candidatos, carga en una sola query las citas activas del día (anti-N+1) y
recorre los tramos del empleado en pasos de `appointmentInterval` restando
ausencias y citas y asignando la primera cabina libre. El resultado se ordena
por hora.

La decisión que respalda es el cruce explícito de seis calendarios distintos
—horario del negocio, horarios de empleados, ausencias, citas activas,
bloqueos generales y cabinas— en una operación de solo lectura sin
paginación. La firma plana del resultado (`List<AvailabilitySlot>`) simplifica
el consumo desde el frontend, que solo muestra los huecos disponibles del día
seleccionado. Cargar las citas en una sola query es una optimización
documentada para evitar el patrón N+1.

## 9. Cadena de filtros de seguridad

Visualiza la pila de filtros de Spring Security en el orden de ejecución:
`RateLimitFilter` (devuelve 429 si se excede el límite), `JwtAuthFilter`
(devuelve 401 si falta el token o es inválido), `TenantGuardFilter` (devuelve
403 si un identity token intenta acceder a recursos tenant o si el
`businessId` del path no coincide con el claim), `DispatcherServlet` y
finalmente el controlador con su `@PreAuthorize` (que devuelve 403 si el rol
no autoriza la operación).

La decisión que respalda es estratificar el rechazo de peticiones por capas
con códigos HTTP semánticamente correctos. Los handlers JSON 401 y 403 se
materializan en `SecurityConfig` para devolver respuestas consistentes con
`ErrorResponse` (campos `timestamp`, `status`, `error`, `message`), evitando
los mensajes HTML por defecto de Spring. El orden importa: el rate-limit va
antes que el JWT para no permitir que un atacante con tokens robados sature
recursos de CPU verificando firmas.

## 10. Secuencia técnica de login

Diagrama de secuencia UML del login con los participantes implicados:
`Frontend`, `AuthController`, `AuthService`, `UserRepository`,
`MembershipRepository`, `BCryptEncoder` y `JwtUtil`. Se muestra explícitamente
la mitigación contra ataques de temporización: si el usuario no existe, el
servicio ejecuta `BCrypt.matches` igualmente contra un hash *dummy* precomputado
para que el tiempo de respuesta sea estadísticamente indistinguible del caso
"usuario existe pero contraseña incorrecta". Después se ramifica según el
número de *memberships* activas para decidir si devuelve un tenant token o un
identity token.

La decisión que respalda es invertir tiempo en mitigaciones que un TFG
podría omitir: ejecución constante de BCrypt y mismo mensaje 401 en los tres
caminos de fallo. El razonamiento se apoya en la regla académica de que un
endpoint de autenticación no debe filtrar información sobre la existencia de
cuentas, incluso si esa información no es directamente explotable.

## 11–13. Versiones de alto nivel (login, crear cita, registro)

Estos tres diagramas son simplificaciones de los flujos detallados (#5, #6,
#7). Cada uno cabe en cinco nodos y se utiliza como apertura de la sección
correspondiente en la defensa: el detalle técnico se reserva para los
diagramas profundos. Su propósito didáctico es asegurar que el tribunal
entienda el *qué* antes de entrar en el *cómo*, evitando que se sature con la
densidad de las versiones completas.

La decisión que respalda es pedagógica: presentar primero la intuición del
flujo y profundizar después, en lugar de bombardear con un único diagrama
hipercompleto. Es un patrón estándar en presentaciones técnicas y se justifica
en la memoria como elección consciente de comunicación.

## 14. Estados de una cita (state machine)

Diagrama de estados UML con las seis transiciones permitidas entre los seis
estados de la entidad `Appointment` (PENDING, CONFIRMED, IN_PROGRESS,
COMPLETED, CANCELLED, NO_SHOW). Las transiciones permitidas se modelan como
un `Map.of(...)` estático en `AppointmentValidator`, lo que evita un *if-else*
anidado y permite añadir nuevos estados o transiciones modificando una sola
estructura.

La decisión que respalda es preferir una *state machine* explícita sobre
flags booleanos (`isCancelled`, `isCompleted`, ...). El catálogo
`appointment_statuses` es global y compartido entre tenants, lo que evita que
cada negocio defina sus propios estados y simplifica la integración con
informes agregados. La ausencia de soft delete sobre `appointments` se
justifica con esta state machine: los estados CANCELLED y NO_SHOW funcionan
como equivalente operativo.

## 15. Password reset

Flujo de recuperación de contraseña introducido en *schema v17*. El usuario
solicita el reset con `POST /api/auth/forgot-password` proporcionando su
email; `PasswordResetService` crea un `PasswordResetToken` con TTL de una
hora y lo persiste en la tabla `password_resets`; `MailService` envía el
enlace con el token. Cuando el usuario lo abre, el frontend llama a
`POST /api/auth/reset-password` con el token y la nueva contraseña; el
servicio valida que el token exista, no esté expirado y no se haya usado, y
si todo está OK actualiza el `password_hash` del usuario con BCrypt y marca
el token como consumido.

La decisión que respalda es asociar el token a la identidad global (`users`)
y no a una membership: la contraseña es de la persona, no del negocio. Un
mismo usuario con varias memberships solo tiene un hash y un flujo de reset.
El TTL corto y el flag de un solo uso son contramedidas estándar contra
*replay* del token.

## 16. Endpoints /api/me

Cuatro endpoints que operan sobre la identidad global, no sobre una
membership: `GET /api/me` devuelve el perfil; `PUT /api/me` permite editar
nombre/teléfono/email (con validación de unicidad del email); `PUT
/api/me/password` cambia la contraseña tras verificar la actual; `GET
/api/me/businesses` devuelve la lista de memberships activas para que el
frontend pinte el selector post-login.

La decisión que respalda es que estos endpoints sean accesibles tanto con
identity token como con tenant token: un usuario debe poder ver su perfil y
sus negocios disponibles incluso antes de elegir uno (caso de uso típico
post-login con N memberships). Por eso `TenantGuardFilter` autoriza
explícitamente `/api/me/*` sin exigir `businessId`.

## 17. Cambio de estado de cita

`PATCH /api/businesses/{businessId}/appointments/{id}/status` con el nuevo
estado en el body. El servicio carga la cita validando tenancy, resuelve el
nuevo estado por nombre (catálogo global), consulta la state machine para
verificar que la transición sea permitida desde el estado actual y, si lo
es, actualiza el campo `id_status`. Las tres salidas de error tienen códigos
HTTP semánticamente correctos: 404 si la cita no pertenece al negocio, 400 si
el nombre del estado es inválido, 409 si la transición no está permitida.

La decisión que respalda es exponer las transiciones como una operación
atómica `PATCH /status` distinta del CRUD genérico, en lugar de aceptar
cambios arbitrarios de cualquier campo con `PUT`. Esto permite enforcear la
state machine en el servicio y devolver mensajes de error contextuales.

## 18. Soft delete y reactivación

Patrón aplicado a ocho de las entidades del proyecto (`businesses`, `users`,
`memberships`, `clients`, `taxes`, `service_categories`, `services`,
`booths`). El `DELETE` no borra físicamente la fila, marca `is_active=false`
y `deactivated_at=NOW()`. Los listados filtran con `WHERE is_active = true`
para ocultar los registros desactivados, mientras que las claves foráneas
desde tablas históricas (p. ej. una cita pasada que referencia a un cliente
ya desactivado) se mantienen intactas, preservando integridad referencial.
La operación inversa `PATCH /reactivate` revierte los dos campos.

La decisión que respalda es preservar el histórico operativo. Si un negocio
da de baja a un empleado y este había atendido citas hace meses, esas citas
no deben perder la referencia al empleado en los informes. *Hard delete* se
reserva para tablas sin valor histórico (`employee_schedules`,
`appointment_services` con `ON DELETE CASCADE`). Las citas en sí no usan
soft delete porque los estados CANCELLED y NO_SHOW cubren el caso.

## 19. Despliegue local con Docker Compose

Topología del entorno local de desarrollo. `docker-compose.yml` define dos
contenedores: `api_optima_dev` (Spring Boot, puerto 8080) y `mysqldb_optima`
(MySQL 8, expuesto en 3307 host / 3306 interno), conectados a través de una
red interna `optima-network`. Las variables sensibles (`DB_PASSWORD`,
`JWT_SECRET`, `MAIL_USERNAME`, `MAIL_PASSWORD`) viven en un fichero `.env`
fuera de git y se inyectan vía `env_file:`. Herramientas externas
(MySQL Workbench, Postman, frontend SPA, Swagger UI) consumen la API en
`http://localhost:8080`.

La decisión que respalda es estandarizar el entorno de desarrollo entre los
tres miembros del equipo: cualquiera puede levantar la pila completa con un
`docker compose up -d` sin instalar MySQL en el host. La exposición de MySQL
en 3307 (no 3306) evita conflictos con instalaciones locales preexistentes.
El uso de Hibernate con `ddl-auto=none` y el SQL aplicado manualmente
(`docs/schema_v20.sql`) garantiza que el esquema sea el mismo en todos los
puestos.

## 20. Funcionamiento básico (para audiencia no técnica)

Diagrama deliberadamente minimalista (cuatro nodos, sin jerga) para que un
miembro del tribunal sin perfil técnico capte de un vistazo qué hace Optima:
un negocio se registra, configura empleados, servicios y horarios, gestiona
sus citas sin solapamientos y atiende y cobra al cliente. No menciona JWT,
multi-tenancy, HTTP, SQL ni ningún concepto técnico.

La decisión que respalda es asumir que la defensa del TFG se evalúa por un
tribunal mixto y que debe haber al menos un diagrama que cualquier
interlocutor entienda. Funciona como cierre afectivo de la presentación o
como diapositiva de portada de la sección funcional.

## 21. Componentes y dependencias entre módulos

Vista estática de la organización del código Java. El paquete raíz se divide
en `config` (configuración), `common` (transversal: filtros, excepciones,
utilidades, mail, geocoding) y `modules` (los seis módulos de dominio: auth,
business, user, client, catalog, appointment). Las flechas continuas
expresan la relación de composición desde `ApiApplication` hacia cada
módulo; las flechas punteadas muestran las dependencias inter-módulo
(p. ej. `appointment` usa `Booth` y `Membership` de `business`, y `Client` de
`client`).

La decisión que respalda es la estructura *feature-based* en lugar de la
clásica *layered* (controller/service/repository). Cada módulo es
autocontenido y reúne sus propios DTOs, entidades, repositorio, servicio y
controlador. Esto minimiza los conflictos de Git en un equipo de tres
personas trabajando en paralelo, ya que cada uno opera en un subdirectorio
distinto. Las dependencias entre módulos quedan acotadas a entidades y
servicios concretos, sin importaciones cíclicas.

## 22. Despliegue propuesto en producción

Arquitectura **propuesta** —no implementada en el TFG— para una hipotética
puesta en producción. Un reverse proxy (Nginx) termina HTTPS con certificados
de Let's Encrypt, sirve el build estático de la SPA en `/` y hace
`proxy_pass` hacia una o varias instancias de Spring Boot bajo `/api/*`. La
BD pasa a MySQL gestionado en un proveedor cloud (RDS, Cloud SQL).
Nominatim y Gmail SMTP se mantienen como integraciones externas.

La decisión que respalda es delimitar claramente el alcance del TFG: el
trabajo se centra en la API, dejando documentado pero no implementado el
hardening de producción (HTTPS, escalado horizontal, secretos en un
*secret manager*, monitorización, CI/CD). Incluir el diagrama en la memoria
demuestra al tribunal que se entiende el camino hacia producción sin haber
inflado el TFG con tareas operativas no académicas.

## 23. Casos de uso UML

Diagrama clásico de casos de uso con tres actores (Admin, Empleado y
Cliente final como actor externo no autenticado) y diez casos de uso
principales: registrar negocio, configurar servicios e impuestos, gestionar
empleados/horarios/ausencias, crear cita, ver agenda, cambiar estado, marcar
pago, recuperar contraseña, configurar cabinas y bloqueos, cambiar de
negocio activo. El cliente final aparece con flecha punteada hacia "crear
cita" porque no usa directamente la aplicación: es gestionado por el
negocio.

La decisión que respalda es la modelización del cliente final como actor
externo. Optima es una herramienta interna del negocio: los clientes finales
no se autentican ni reservan online en la versión actual. Si en una
iteración futura se añadiera un portal público para clientes, ese actor
pasaría a tener acceso autenticado. Documentar esta frontera ahora evita
malentendidos en el tribunal sobre el alcance del proyecto.

## 24. Gestión de empleados

Detalla las cuatro operaciones que un admin realiza sobre los empleados de
su negocio: alta vía `POST /users` (con *find-or-create* del `User` por
email y creación de una `Membership` con rol EMPLOYEE), añadir horario
semanal con `POST /users/{id}/schedules` (validando que no solape con otro
horario del mismo día), añadir ausencia puntual con `POST /users/{id}/absences`,
y soft delete con `DELETE /users/{id}` (que marca `is_active=false` en la
membership, no en el usuario).

La decisión que respalda es la separación entre identidad y pertenencia:
dar de alta a un "empleado" en el negocio A no implica crear un usuario
nuevo si esa persona ya existe en el sistema; solo se le añade una nueva
membership. Esta semántica permite que una misma persona trabaje en varios
negocios con rolas distintos sin duplicar credenciales.

## 25. Modelo de clases JPA (aproximación)

Aproximación a un diagrama de clases UML que muestra las 17 entidades JPA
con sus campos clave y las relaciones `@OneToMany` y `@ManyToOne`. Las
relaciones marcadas con flecha continua son obligatorias; las punteadas
representan FKs nullable (p. ej. `Appointment.booth` o
`ScheduleBlock.membership/booth`, donde el modelo permite los tres tipos de
bloqueo: global, por empleado o por cabina). El diagrama complementa al ER
(#3) añadiendo el ángulo orientado a objetos: tipos Java (`BigDecimal`,
`LocalDateTime`) y nombres de clase (`BusinessService` para la tabla
`services`, `BookedService` para `appointment_services` —renombrados para
evitar colisión con la anotación `@Service` de Spring y con la capa
`AppointmentService`).

La decisión que respalda es el uso de objetos del dominio en las relaciones
JPA (`private Business business;`) en lugar de IDs sueltos (`private Long
idBusiness;`). Hibernate gestiona la carga *lazy* y el desarrollador trabaja
con grafos de objetos, lo que mejora la legibilidad del código en la capa de
servicio. Esta convención está documentada explícitamente en el patrón de
entidad canónica del proyecto.

## 26. Journey de un empleado (día a día)

Diagrama narrativo en lenguaje cotidiano que recorre la jornada de un
empleado del negocio: entra a Optima con sus credenciales, consulta la
agenda del día, atiende a cada cliente que va llegando y va marcando los
cambios de estado de la cita (En curso → Completada → Pagada). Las dos
ramas alternativas —Cancelada y No presentada— se representan con flechas
punteadas para cubrir los casos en que un cliente no acude. El bucle de
*siguiente cliente* enfatiza el carácter cíclico de la jornada.

La decisión que respalda es que la herramienta debe poder operarse con
vocabulario natural por parte de personal de recepción sin formación
técnica. Los seis estados que la *state machine* (#14) modela formalmente
—PENDING, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW— se
traducen aquí a expresiones del lenguaje del día a día ("en curso",
"completada", "pagada", "cancelada", "no presentada"), demostrando que el
modelo técnico se mapea bien a la experiencia real del usuario.

## 27. Journey de un administrador

Recorrido en lenguaje plano del dueño del negocio desde el alta hasta la
operación diaria. Se distinguen dos fases: la configuración inicial *one-shot*
(registro, definición de servicios y precios, alta de empleados con sus
horarios, definición del horario de apertura, configuración de cabinas) y la
operación recurrente (revisar la agenda, crear citas, actualizar o desactivar
servicios o empleados). El bucle desde "Optima evita solapamientos" hacia
"Cada día revisa la agenda" cierra el ciclo de operación.

La decisión que respalda es que el alta de un nuevo tenant debe ser un
proceso de menos de diez minutos sin intervención humana. Esa premisa
justifica el auto-registro público documentado en #6, la jerarquía sencilla
de catálogos (categorías → servicios) y el patrón de soft delete (#18) que
permite al admin revertir errores sin perder el histórico operativo.

## 28. Journey de un cliente final

Diagrama narrativo desde la perspectiva del cliente final, que **no**
interactúa con la aplicación en ningún momento: contacta con el negocio por
teléfono o presencialmente, alguien del negocio crea la cita en Optima, el
cliente acude el día previsto, recibe el servicio y paga. El recorrido
nunca toca Optima directamente.

La decisión que respalda es la frontera de alcance del TFG: la versión
actual es una herramienta interna del negocio, no un portal público de
reservas. Esta separación debe quedar clara en la defensa para evitar
malentendidos. Si en una iteración futura se añadiera un portal de cliente,
el actor "Cliente final" pasaría de ser externo (flecha punteada en #23
Casos de uso) a actor autenticado con sus propios casos de uso (reservar,
cancelar, consultar histórico).

---

## Notas de cierre

- Los diagramas del FigJam son referencias visuales: la memoria escrita debe
  contener la prosa expandida (que se encuentra en este documento) y, donde
  proceda, las exportaciones PNG/PDF de los frames de Figma.
- Los diagramas #11–#13 (versiones de alto nivel), #20 (funcionamiento
  básico) y #26–#28 (journeys de usuario en lenguaje plano) están pensados
  para diapositivas y narrativa de la defensa; el resto suele ir como
  figura numerada en la memoria escrita con su correspondiente *pie de figura*.
- El tool `generate_diagram` de Figma MCP no soporta `classDiagram` nativo,
  por eso el diagrama #25 es una aproximación con flowchart. Si la memoria
  exige un class diagram UML estricto, conviene rehacerlo con PlantUML o
  Mermaid CLI exportando a PNG.
- Los títulos de cada diagrama en FigJam corresponden al campo `name`
  pasado al tool (p. ej. "07 - Crear cita con validaciones"). Si se quieren
  títulos más prominentes embebidos dentro del propio diagrama, hay que
  regenerarlos añadiendo un nodo cabecera en el código Mermaid.

---

## 29. ER completo con todos los atributos (sustituye a #3)

Diagrama entidad-relación detallado de las 17 tablas del *schema v20* con
todos sus campos, tipos, claves primarias, foráneas y restricciones de
unicidad. Cubre las 11 tablas que el #3 sólo mostraba como cajas vacías
(`taxes`, `clients`, `service_categories`, `services`, `booths`,
`business_hours`, `employee_schedules`, `employee_absences`,
`schedule_blocks`, `appointment_statuses`, `password_resets`) y añade en
`appointments` las dos columnas virtuales `active_slot_key` y
`active_booth_slot_key` con su UNIQUE asociada.

La decisión que respalda es exponer todo el contrato de datos a nivel SQL
para que el tribunal pueda contrastar el modelo entidad-relación con el
schema real de MySQL. Las columnas virtuales son una técnica clave para
prevenir concurrencia en BD sin recurrir a triggers ni a tablas auxiliares,
y se materializan en el diagrama #35.

## 30. PATCH /payment (marcar cita como pagada)

Endpoint dedicado `PATCH /api/businesses/{businessId}/appointments/{id}/payment`
para alternar `is_paid` en una cita. Body con `UpdatePaymentRequest`
(record con `@NotNull Boolean isPaid`). El endpoint no lleva
`@PreAuthorize`: tanto ADMIN como EMPLOYEE pueden marcar el pago (operativa
de recepción cotidiana). La operación actualiza también `updated_at` vía
`@PreUpdate`.

La decisión que respalda es ofrecer un endpoint atómico para una operación
muy frecuente, en lugar de obligar al cliente a usar `PUT /appointments/{id}`
con todos los campos. Esto reduce el riesgo de sobrescribir accidentalmente
otros campos y deja claro en los logs que la mutación es de naturaleza
"pago".

## 31. CRUD de cabinas (Booth)

Operaciones completas del recurso `Booth` bajo
`/api/businesses/{id}/booths`: POST con name único en el negocio, GET
paginado filtrando `is_active=true`, GET por id con validación cross-tenant,
PUT que permite renombrar respetando la unicidad, y DELETE que aplica soft
delete (`is_active=false`, `deactivated_at=NOW()`).

La decisión que respalda es modelar la cabina como recurso de primer
nivel del negocio (sala, silla, box, bahía), independiente del empleado.
Esta separación permite que dos empleados libres no sirvan si solo hay una
cabina disponible, restricción física que el #4 multi-tenancy y el #7
crear cita validan a la hora de agendar.

## 32. CRUD de schedule_blocks (3 tipos)

Operaciones del recurso `ScheduleBlock` bajo `/schedule-blocks`. POST acepta
`startDate`, `endDate`, `reason` y opcionalmente `idMembership` o `idBooth`
(nunca los dos a la vez). El `CHECK chk_block_target` añadido en *schema
v19* garantiza a nivel BD que `id_membership IS NULL OR id_booth IS NULL`,
modelando los tres tipos: global (ambos NULL), por empleado, por cabina.
Hard delete (sin PUT por decisión de diseño: si el admin se equivoca,
borra y crea).

La decisión que respalda es separar bloqueos de días completos
(`schedule_blocks`) de bloqueos por rangos de horas (`employee_absences`).
Los primeros son apropiados para festivos del negocio, vacaciones largas o
mantenimiento de cabina; los segundos para una cita médica de un empleado.
El `CHECK` en BD evita que un cliente API mal codificado introduzca filas
con ambas FKs rellenas, lo que rompería la semántica de
`findApplicableBlocks` (#36).

## 33. Gestión de impuestos (Tax CRUD)

CRUD completo del recurso `Tax` bajo `/api/businesses/{id}/taxes`: POST con
`name` único en el negocio y `percentage` (DECIMAL 5,2) entre 0 y 100,
listados paginados, PUT que permite cambiar el porcentaje, DELETE como
soft delete. El detalle clave: cambiar el porcentaje de un impuesto **no**
afecta a las citas históricas, porque `BookedService.applied_tax_percentage`
se congela en el momento de la reserva.

La decisión que respalda es que cada negocio define sus propios impuestos
y la facturación de citas pasadas se mantiene fiel a la regulación vigente
en el momento de la reserva. Si más tarde cambia el IVA (p. ej. del 10%
al 21%), las citas anteriores siguen mostrando el porcentaje aplicado en
su día, sin necesidad de bitácoras adicionales.

## 34. Configuración de catálogo (categorías y servicios)

Doble CRUD del módulo `catalog`: `ServiceCategory` (entidad ligera con
`name` único en el negocio) y `BusinessService` (con FK a la categoría y
al impuesto, más `price` BigDecimal y `duration_minutes` int). El servicio
valida cross-tenant que tanto la categoría como el impuesto referidos
pertenezcan al mismo negocio que el servicio que se crea. Misma semántica
de soft delete que en `Tax` y misma garantía de inmutabilidad histórica
vía `BookedService.applied_price` frozen.

La decisión que respalda es la jerarquía de catálogo de dos niveles
(Categoría → Servicio) en vez de tres o más. Es suficientemente expresiva
para un pequeño negocio (p. ej. "Cabello → Corte, Tinte, Mechas"; "Manos →
Manicura, Esmalte") sin caer en sobreingeniería. La validación cross-tenant
de categoría e impuesto evita que un admin malicioso o despistado pueda
referenciar recursos de otro negocio.

## 35. Pessimistic lock + concurrent booking

Diagrama secuencial que muestra cómo se protege la creación concurrente
de citas. La transacción A obtiene `PESSIMISTIC_WRITE` sobre las filas de
`appointments` del empleado X antes de validar overlaps; la transacción
concurrente B se bloquea hasta que A confirma. Cuando B desbloquea, repite
la query y detecta la cita que A acaba de insertar, devolviendo 409
Conflict. Como defensa en profundidad, las columnas virtuales generadas
`active_slot_key` y `active_booth_slot_key` con UNIQUE constraint abortan
a nivel BD cualquier inserción duplicada que esquivara el lock.

La decisión que respalda es no confiar exclusivamente en el lock optimista
ni en validaciones a nivel de aplicación. La generación de columnas
virtuales que sólo se materializan cuando la cita está en estado activo
(1=PENDING, 2=CONFIRMED, 3=IN_PROGRESS) es una técnica avanzada que
permite tener UNIQUE constraints "condicionales" sin recurrir a triggers
ni a índices parciales (que MySQL no soporta).

## 36. findApplicableBlocks (query JPQL con 3 OR)

Detalle de la query del repositorio `ScheduleBlockRepository` que el
validador llama para detectar bloqueos aplicables al crear una cita. La
cláusula `WHERE` tiene tres alternativas unidas con OR: bloqueo global
(`id_membership IS NULL AND id_booth IS NULL`), bloqueo por empleado
(`id_membership = :membershipId`), bloqueo por cabina (`id_booth =
:boothId`). Todas comparten el `AND id_business = :businessId AND :date
BETWEEN start_date AND end_date`.

La decisión que respalda es resolver los tres tipos de bloqueo en una
única query, en lugar de hacer tres consultas separadas y unirlas en
Java. Esto reduce el tráfico con la BD y aprovecha el índice compuesto
sobre `(id_business, start_date, end_date)`. La estructura modular
(global / empleado / cabina) se modela como FKs nullables con el `CHECK`
que enforza la exclusividad (#32).

## 37. @EntityGraph anti-N+1

Optimización aplicada a los listados paginados para evitar el patrón N+1
clásico de JPA. El caso más ilustrativo es el de las citas: sin
optimización, listar 20 citas con cuatro relaciones lazy (`client`,
`membership`, `status`, `booth`) genera 1+80 queries (`SELECT FROM
appointments` + 4 selects por cada cita). La anotación
`@EntityGraph(attributePaths = {"client", "membership",
"membership.user", "status", "booth"})` en el método del repository
fuerza un `LEFT JOIN FETCH` que carga todo en una sola query. El mismo
patrón se aplica hoy a seis repositorios —`AppointmentRepository`,
`MembershipRepository`, `BusinessServiceRepository`,
`ScheduleBlockRepository`, `EmployeeAbsenceRepository` y
`EmployeeScheduleRepository`—, ya que todos sus Response DTO aplanan
relaciones `@ManyToOne` que dispararían un select lazy por fila. Para
`BookedService` se usa adicionalmente *batch fetch* con un IN sobre los
IDs de citas.

La decisión que respalda es priorizar el rendimiento del listado por
encima de la elegancia del lazy fetching por defecto de Hibernate. En
un endpoint que el frontend llama al pintar el calendario diario, la
diferencia entre 81 y 1 queries cambia perceptiblemente la latencia
percibida por el usuario.

## 38. GlobalExceptionHandler (16 handlers)

Estructura del manejo centralizado de excepciones vía
`@RestControllerAdvice` en `common/exception/GlobalExceptionHandler.java`.
Los 16 métodos `@ExceptionHandler` cubren toda la superficie HTTP: la
excepción semántica del propio dominio (`ResponseStatusException`), las de
validación de entrada (`MethodArgumentNotValidException` para el
`@RequestBody`; `ConstraintViolationException` para path y query params;
`MethodArgumentTypeMismatchException`; `PropertyReferenceException` para un
`sort` inexistente; `NumberFormatException` / `ConversionFailedException`;
`MissingServletRequestParameterException`; `IllegalArgumentException`), las
de protocolo HTTP (`HttpMessageNotReadableException`,
`HttpRequestMethodNotSupportedException`,
`HttpMediaTypeNotSupportedException`, `NoResourceFoundException`), la de
seguridad de método (`AccessDeniedException`, lanzada por `@PreAuthorize`),
las de persistencia (`EntityNotFoundException`,
`DataIntegrityViolationException`), y el catch-all (`Exception`) que
registra con `log.error` y devuelve 500. Todas serializan a un record
`ErrorResponse` con cuatro campos (`timestamp`, `status`, `error`,
`message`). Los rechazos de autenticación (token ausente o inválido) no
pasan por aquí: los resuelve el `AuthenticationEntryPoint` configurado en
`SecurityConfig`.

La decisión que respalda es enforcear una única forma de respuesta de
error en toda la API. El frontend solo necesita conocer la estructura
`ErrorResponse` para parsear cualquier fallo, lo que simplifica
muchísimo el manejo en el cliente. El catch-all evita que excepciones
inesperadas filtren stack traces de Spring al usuario.

## 39. Arquitectura de testing (H2 in-memory)

Topología de la suite de tests del proyecto. `mvnw test` ejecuta 21 tests
repartidos en 7 clases sin necesidad de Docker porque
`src/test/resources/application.properties` apunta a una BD H2 en memoria
con `MODE=MySQL` (lo que permite emular tipos y constraints de MySQL) y
`ddl-auto=create-drop` (Hibernate recrea el schema en cada test).
La cobertura cruza varias capas: `AuthServiceTest` (7 casos cubriendo los
dos caminos del login + select-business OK/403), `AppointmentServiceTest`
(overlap, ausencias y validaciones cross-tenant), `AvailabilityServiceTest`
(caso límite con negocio cerrado), `AppointmentValidatorTest` (reglas de
validación de citas), `TenantGuardFilterTest` (aislamiento cross-tenant del
filtro de seguridad), `AppointmentControllerWebMvcTest` (un `@WebMvcTest`
que ejercita la capa web aislada) y `ApiApplicationTests.contextLoads`
(sanity check del contexto de Spring).

La decisión que respalda es separar el contrato de tests del entorno de
desarrollo. La compañera puede ejecutar `mvnw test` en cualquier máquina
sin levantar Docker, lo que acelera el ciclo. Usar `MODE=MySQL` en H2 es
preferible a un mock total porque ejerce las queries reales de JPA contra
un motor SQL real, capturando errores de sintaxis o cardinalidad que un
mock pasaría por alto.

## 40. Timing attack mitigation (dummy BCrypt)

Detalle de la mitigación contra ataques de temporización en el login.
Cuando `UserRepository.findByEmailIgnoreCase` devuelve `Optional.empty()`,
el servicio invoca `BCrypt.matches(password, DUMMY_HASH)` contra un hash
precomputado en lugar de devolver directamente 401. Esto iguala el tiempo
de respuesta entre las ramas "usuario inexistente" y "contraseña
incorrecta" (~85 ms). La verificación empírica del 2026-05-18 confirmó
que los usuarios fantasma tardan 235-271 ms (orden similar al de un
login con contraseña errónea), lo que cierra el oráculo de temporización.

La decisión que respalda es invertir tiempo en una mitigación que un TFG
podría omitir como sobreingeniería. La razón académica es que un endpoint
de autenticación no debe filtrar información sobre la existencia de
cuentas, incluso si esa información parece inocua. La medición empírica
documentada en el log de la auditoría refuerza la defensa frente al
tribunal: la mitigación no es teórica, está verificada.
