package com.optima.api.modules.appointment.service;

import com.optima.api.modules.appointment.dto.request.CreateAppointmentRequest;
import com.optima.api.modules.appointment.dto.request.UpdateAppointmentStatusRequest;
import com.optima.api.modules.appointment.dto.request.UpdatePaymentRequest;
import com.optima.api.modules.appointment.dto.response.AppointmentResponse;
import com.optima.api.modules.appointment.model.Appointment;
import com.optima.api.modules.appointment.model.AppointmentStatus;
import com.optima.api.modules.appointment.model.BookedService;
import com.optima.api.modules.appointment.repository.AppointmentRepository;
import com.optima.api.modules.appointment.repository.AppointmentStatusRepository;
import com.optima.api.modules.appointment.repository.BookedServiceRepository;
import com.optima.api.modules.business.model.Booth;
import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.model.Membership;
import com.optima.api.modules.business.repository.BoothRepository;
import com.optima.api.modules.business.repository.BusinessRepository;
import com.optima.api.modules.business.repository.MembershipRepository;
import com.optima.api.modules.catalog.model.BusinessService;
import com.optima.api.modules.catalog.repository.BusinessServiceRepository;
import com.optima.api.modules.client.model.Client;
import com.optima.api.modules.client.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AppointmentService - Logica de negocio del modulo appointment.
 *
 * Es el service mas complejo del proyecto: orquesta 8 repositorios y un
 * validator dedicado para crear, leer y transicionar citas con todas
 * las reglas de negocio aplicadas.
 *
 * COMUNICACION:
 * - Lo invoca: AppointmentController.
 * - Llama a:
 *     AppointmentRepository           CRUD + existsOverlapping (query custom).
 *     AppointmentStatusRepository     findByName para resolver "PENDING" etc.
 *     BookedServiceRepository         persiste y consulta servicios reservados.
 *     BoothRepository                 verifica cabina cross-tenant + activa (con lock pesimista).
 *     BusinessRepository              verifica negocio + lee appointmentInterval.
 *     BusinessServiceRepository       verifica que cada servicio existe en el tenant.
 *     ClientRepository                verifica cliente cross-tenant + activo.
 *     MembershipRepository            verifica empleado (membership) cross-tenant + activo.
 *     AppointmentValidator            las reglas complejas de negocio.
 * - Devuelve: AppointmentResponse (con la lista de bookedServices).
 *
 * Patron BookedService congelado: cuando se crea una cita, se guarda una
 * copia del precio y porcentaje de impuesto del momento. Si manana el
 * negocio sube el precio, las citas pasadas siguen mostrando el valor
 * acordado en su dia.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusRepository statusRepository;
    private final BookedServiceRepository bookedServiceRepository;
    private final BoothRepository boothRepository;
    private final BusinessRepository businessRepository;
    private final BusinessServiceRepository serviceRepository;
    private final ClientRepository clientRepository;
    private final MembershipRepository membershipRepository;
    private final AppointmentValidator validator;

    /**
     * Crea una nueva cita con sus servicios asociados.
     *
     * QUE HACE EN UNA FRASE:
     * Recibe (clientId, membershipId, serviceIds[], boothId?, startDateTime, notes?),
     * aplica 14 validaciones, persiste la cita en estado PENDING y crea un
     * BookedService por cada servicio congelando precio y porcentaje de impuesto.
     *
     * Pasos (15 validaciones encadenadas):
     *    1. Verifica que el negocio existe (404 si no).
     *    2. Cross-tenant: cliente pertenece a este negocio (404 si no).
     *    3. Cliente debe estar activo (400 si esta desactivado).
     *    4. Cross-tenant: empleado pertenece a este negocio (404 si no).
     *    5. Empleado debe estar activo (400 si esta desactivado).
     *    6. Hora respeta el appointmentInterval del negocio (400 si no).
     *    7. Cross-tenant: cada servicio pertenece al negocio (404 si no).
     *    8. Cada servicio debe estar activo (400 si esta desactivado).
     *    9. Calcula endDateTime sumando duraciones de los servicios.
     *   10. La cita encaja en el horario del empleado (400 si no o
     *       si cruza medianoche).
     *   11. No solapa con otra cita activa del empleado (409 si si).
     *   12. No solapa con una ausencia registrada del empleado (409 si si).
     *   13. Si lleva cabina: cross-tenant + activa + sin solapamiento
     *       de cabina (404/400/409).
     *   14. No choca con un bloqueo de agenda (global, por empleado
     *       o por cabina) (409 si si).
     *   15. Existe el estado PENDING en BD (500 si no, error de seed).
     *
     * Por que precios CONGELADOS en BookedService: si manana el negocio
     * sube el precio de "Corte de pelo" de 15 a 20 EUR, las citas
     * pasadas DEBEN seguir mostrando 15 EUR (lo acordado en su dia).
     *
     * @param businessId barrera multi-tenant: TODO se valida contra este id.
     * @param request payload validado: clientId, membershipId, serviceIds, boothId (opcional), startDateTime, notes.
     * @return AppointmentResponse con la cita creada + bookedServices.
     */
    public AppointmentResponse createAppointment(Long businessId, CreateAppointmentRequest request) {

        // 1. Buscar el negocio (necesitamos el appointmentInterval)
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el negocio con ID: " + businessId
                ));

        // 2. Cross-tenant: el cliente pertenece a este negocio
        Client client = clientRepository.findByIdAndBusinessId(
                        request.clientId(), businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el cliente con ID: " + request.clientId()
                                + " en el negocio con ID: " + businessId
                ));

        // 2b. El cliente debe estar activo
        if (!client.getIsActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El cliente con ID: " + request.clientId() + " está desactivado"
            );
        }

        // 3. Cross-tenant: la membership (empleado en este negocio) existe.
        //    [v16 membership] El membershipId del request es realmente el id
        //    de la membership; se mantiene el nombre externo por
        //    compatibilidad del API.
        //
        //    Lock pesimista (SELECT ... FOR UPDATE) sobre la membership: dos
        //    POST concurrentes al mismo empleado se serializan hasta el commit,
        //    impidiendo el TOCTOU entre validateNoOverlap (paso 8) y el INSERT
        //    del paso 10 (double-booking). Aceptable porque la transaccion es
        //    corta y no hace llamadas externas.
        Membership membership = membershipRepository.findByIdAndBusinessIdForUpdate(
                        request.membershipId(), businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el empleado con ID: " + request.membershipId()
                                + " en el negocio con ID: " + businessId
                ));

        // 3b. La membership debe estar activa (equivale al user activo de v15).
        if (!membership.getIsActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El empleado con ID: " + request.membershipId() + " está desactivado"
            );
        }

        // 4. Validar que la hora respeta el intervalo del negocio
        //    (solo necesita startDateTime, no depende de endDateTime)
        validator.validateAppointmentInterval(
                request.startDateTime(),
                business.getAppointmentInterval()
        );

        // 5. Buscar los servicios y validar cada uno (cross-tenant directo en la query)
        List<BusinessService> services = new ArrayList<>();
        for (Long serviceId : request.serviceIds()) {
            BusinessService service = serviceRepository
                    .findByIdAndBusinessId(serviceId, businessId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "No se encontró el servicio con ID: " + serviceId
                                    + " en el negocio con ID: " + businessId
                    ));

            // El servicio debe estar activo
            if (!service.getIsActive()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El servicio con ID: " + serviceId + " está desactivado"
                );
            }

            services.add(service);
        }

        // 6. Calcular endDateTime sumando las duraciones de los servicios
        //    El frontend no manda endDateTime, lo calculamos aquí.
        int totalMinutes = services.stream()
                .mapToInt(BusinessService::getDurationMinutes)
                .sum();

        LocalDateTime endDateTime = request.startDateTime().plusMinutes(totalMinutes);

        // 7. Validar que la cita cae dentro del horario del empleado
        //    (ahora sí tenemos endDateTime calculado)
        validator.validateEmployeeSchedule(
                request.membershipId(),
                request.startDateTime(),
                endDateTime
        );

        // 8. Validar que no hay solapamiento con otra cita del empleado
        validator.validateNoOverlap(
                request.membershipId(),
                request.startDateTime(),
                endDateTime
        );

        // 8a. La cita no puede caer sobre una ausencia registrada del empleado.
        //     GET /availability ya excluye estos huecos; POST debe rechazar
        //     el mismo intervalo para que el calendario sea coherente aunque
        //     el cliente salte la consulta previa.
        validator.validateNoEmployeeAbsence(
                request.membershipId(),
                request.startDateTime(),
                endDateTime
        );

        // 8b. Si la cita lleva cabina: cross-tenant + activa + overlap.
        //     Si no lleva (boothId=null), se omite todo este bloque.
        //
        //     Lock pesimista sobre la cabina (analogo al de Membership en el
        //     paso 3): dos POST con empleados distintos compartiendo cabina
        //     se serializan aqui, garantizando la regla "1 empleado por
        //     cabina y slot" frente al TOCTOU de validateNoBoothOverlap.
        Booth booth = null;
        if (request.boothId() != null) {
            booth = boothRepository
                    .findByIdAndBusinessIdForUpdate(request.boothId(), businessId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "No se encontró la cabina con ID: " + request.boothId()
                                    + " en el negocio con ID: " + businessId
                    ));

            if (!booth.getIsActive()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "La cabina con ID: " + request.boothId() + " está desactivada"
                );
            }

            validator.validateNoBoothOverlap(
                    request.boothId(),
                    request.startDateTime(),
                    endDateTime
            );
        }

        // 8c. La fecha de la cita no puede caer en un bloqueo de agenda
        //     (global / por empleado / por cabina) -> 409 si choca.
        validator.validateNoScheduleBlock(
                businessId,
                request.membershipId(),
                request.boothId(),
                request.startDateTime()
        );

        // 9. Buscar el estado PENDING. Si no existe es un error de configuración
        //    del servidor (faltan los INSERT del schema), por eso 500 INTERNAL_SERVER_ERROR.
        AppointmentStatus pendingStatus = statusRepository.findByName("PENDING")
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Error de configuración: no se encontró el estado PENDING "
                                + "en la base de datos. Ejecutar los INSERT del schema."
                ));

        // 10. Crear y guardar la cita
        Appointment appointment = new Appointment();
        appointment.setBusiness(business);
        appointment.setClient(client);
        appointment.setMembership(membership);
        appointment.setBooth(booth);  // null si la cita no usa cabina
        appointment.setStatus(pendingStatus);
        appointment.setStartDateTime(request.startDateTime());
        appointment.setEndDateTime(endDateTime);
        appointment.setNotes(request.notes());

        Appointment saved = appointmentRepository.save(appointment);

        // 11. Crear los BookedService con precios congelados
        List<BookedService> bookedServices = new ArrayList<>();
        for (BusinessService service : services) {
            BookedService booked = new BookedService();
            booked.setAppointment(saved);
            booked.setService(service);

            // Congelamos el precio actual del servicio
            booked.setAppliedPrice(service.getPrice());

            // Congelamos el porcentaje del impuesto actual
            booked.setAppliedTaxPercentage(service.getTax().getPercentage());

            bookedServices.add(booked);
        }

        List<BookedService> savedBookedServices =
                bookedServiceRepository.saveAll(bookedServices);

        // 12. Devolver respuesta — pasamos la lista que acabamos de persistir
        //     para que el DTO no tenga que volver a consultar el repositorio.
        return AppointmentResponse.from(saved, savedBookedServices);
    }

    /**
     * Busqueda paginada de citas con filtros opcionales `from`, `to` y
     * `membershipId`.
     *
     * Semantica de fechas: el caller pasa `from`/`to` como dias completos
     * (LocalDate). Convertimos `from` a inicio del dia (00:00 inclusive) y
     * `to` a inicio del dia siguiente (cota superior exclusive), asi
     * "del 2027-03-15 al 2027-03-15" captura todas las citas del 15.
     *
     * El batch loading del N+1 se aplica sobre `page.getContent()`: una sola
     * query a `appointment_services` con `id_appointment IN (...)` para todas
     * las citas de la pagina actual.
     */
    @Transactional(readOnly = true)
    public Page<AppointmentResponse> searchAppointments(Long businessId,
                                                       LocalDate from,
                                                       LocalDate to,
                                                       Long membershipId,
                                                       Pageable pageable) {
        LocalDateTime fromInclusive = from != null ? from.atStartOfDay() : null;
        LocalDateTime toExclusive = to != null ? to.plusDays(1).atStartOfDay() : null;

        Page<Appointment> appointmentPage = appointmentRepository.searchAppointments(
                businessId, fromInclusive, toExclusive, membershipId, pageable);

        List<Appointment> appointments = appointmentPage.getContent();
        if (appointments.isEmpty()) {
            return appointmentPage.map(a -> AppointmentResponse.from(a, List.of()));
        }

        List<Long> appointmentIds = appointments.stream()
                .map(Appointment::getId)
                .toList();

        Map<Long, List<BookedService>> bookedServicesByAppointmentId =
                bookedServiceRepository.findAllByAppointmentIdIn(appointmentIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                bs -> bs.getAppointment().getId()
                        ));

        return appointmentPage.map(a -> AppointmentResponse.from(
                a,
                bookedServicesByAppointmentId.getOrDefault(a.getId(), List.of())
        ));
    }

    /**
     * Busca una cita por ID dentro de un negocio (protección cross-tenant).
     */
    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentById(Long businessId, Long id) {
        Appointment appointment = appointmentRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la cita con ID: " + id
                                + " en el negocio con ID: " + businessId
                ));

        List<BookedService> bookedServices =
                bookedServiceRepository.findAllByAppointmentId(appointment.getId());
        return AppointmentResponse.from(appointment, bookedServices);
    }

    /**
     * Cambia el estado de una cita, validando que la transición sea legal.
     */
    public AppointmentResponse updateAppointmentStatus(Long businessId,
                                                       Long appointmentId,
                                                       UpdateAppointmentStatusRequest request) {

        // 1. Buscar la cita (con protección cross-tenant)
        Appointment appointment = appointmentRepository
                .findByIdAndBusinessId(appointmentId, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la cita con ID: " + appointmentId
                                + " en el negocio con ID: " + businessId
                ));

        // 2. Buscar el nuevo estado por nombre. Un nombre que no existe es
        //    input invalido del cliente (no un recurso ausente del catalogo),
        //    por eso 400 BAD_REQUEST con la lista completa de estados validos.
        AppointmentStatus newStatus = statusRepository
                .findByName(request.statusName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El estado '" + request.statusName() + "' no es válido. "
                                + "Estados permitidos: PENDING, CONFIRMED, IN_PROGRESS, "
                                + "COMPLETED, CANCELLED, NO_SHOW"
                ));

        // 3. Validar que la transición es permitida
        String currentStatusName = appointment.getStatus().getName();
        validator.validateStatusTransition(currentStatusName, request.statusName());

        // 4. Aplicar el cambio
        appointment.setStatus(newStatus);

        // 5. Guardar y devolver
        Appointment updated = appointmentRepository.save(appointment);
        List<BookedService> bookedServices =
                bookedServiceRepository.findAllByAppointmentId(updated.getId());
        return AppointmentResponse.from(updated, bookedServices);
    }

    /**
     * Marca una cita como pagada o no pagada (operacion de pago independiente
     * del flujo de estados). Cross-tenant safe; 404 si la cita no esta en
     * este negocio. Devuelve el AppointmentResponse actualizado con isPaid
     * reflejado.
     */
    public AppointmentResponse markPayment(Long businessId,
                                           Long appointmentId,
                                           UpdatePaymentRequest request) {

        Appointment appointment = appointmentRepository
                .findByIdAndBusinessId(appointmentId, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la cita con ID: " + appointmentId
                                + " en el negocio con ID: " + businessId
                ));

        appointment.setIsPaid(request.isPaid());
        Appointment updated = appointmentRepository.save(appointment);

        List<BookedService> bookedServices =
                bookedServiceRepository.findAllByAppointmentId(updated.getId());
        return AppointmentResponse.from(updated, bookedServices);
    }
}