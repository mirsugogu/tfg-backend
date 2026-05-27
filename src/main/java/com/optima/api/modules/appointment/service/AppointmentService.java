package com.optima.api.modules.appointment.service;

import com.optima.api.modules.appointment.dto.request.CreateAppointmentRequest;
import com.optima.api.modules.appointment.dto.request.UpdateAppointmentRequest;
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
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio con la logica principal de citas.
 * Valida disponibilidad, estados y servicios reservados.
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
     * Crea una cita y guarda los servicios con el precio e impuesto actuales.
     */
    public AppointmentResponse createAppointment(Long businessId, CreateAppointmentRequest request) {

        // 1. Buscar el negocio (necesitamos el appointmentInterval)
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el negocio con ID: " + businessId
                ));

        // 1b. El negocio debe estar activo: un negocio desactivado no acepta
        //     nuevas citas (ver Business.java, javadoc del campo isActive).
        if (!Boolean.TRUE.equals(business.getIsActive())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El negocio con ID: " + businessId + " está desactivado"
            );
        }

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

        // 3. Cross-tenant: la membership del empleado existe en este negocio.
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

        // 6b. Validar que la cita cae dentro del horario de apertura del negocio
        //     (restriccion mas general: si el negocio esta cerrado, no se acepta
        //     cita aunque el empleado tenga schedule ese dia). Coherente con
        //     GET /availability (que devuelve [] cuando el negocio esta cerrado).
        validator.validateBusinessHours(
                businessId,
                request.startDateTime(),
                endDateTime
        );

        // 7. Validar que la cita cae dentro del horario del empleado
        //    (ahora sí tenemos endDateTime calculado)
        validator.validateEmployeeSchedule(
                request.membershipId(),
                request.startDateTime(),
                endDateTime
        );

        // 8. Validar que no hay solapamiento con otra cita del empleado.
        //    excludeAppointmentId=null porque es una creacion: no hay "propia
        //    cita" que excluir del check.
        validator.validateNoOverlap(
                request.membershipId(),
                request.startDateTime(),
                endDateTime,
                null
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
                    endDateTime,
                    null
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

        // saveAndFlush fuerza el INSERT inmediatamente para que cualquier
        // violacion de los UNIQUE uq_appointment_active_slot /
        // uq_appointment_active_booth_slot (definidos sobre columnas
        // virtuales en docs/schema_v20.sql) salte AQUI y no al commit.
        // Esto cierra posibles carreras: los locks pesimistas sobre
        // Membership/Booth solo serializan la
        // fila de la entidad, no el predicado "no hay otra cita activa
        // en este slot"; el UNIQUE a nivel BD si.
        Appointment saved;
        try {
            saved = appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            if (msg.contains("uq_appointment_active_slot")) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "El empleado ya tiene una cita en ese horario"
                );
            }
            if (msg.contains("uq_appointment_active_booth_slot")) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "La cabina ya tiene una cita en ese horario"
                );
            }
            throw ex;
        }

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

    /** Campos permitidos para ordenar la busqueda de citas. */
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "startDateTime", "endDateTime", "createdAt", "isPaid");

    /** Busca citas con paginacion y filtros opcionales. */
    @Transactional(readOnly = true)
    public Page<AppointmentResponse> searchAppointments(Long businessId,
                                                       LocalDate from,
                                                       LocalDate to,
                                                       Long membershipId,
                                                       Pageable pageable) {
        // Rechaza un ?sort= por un campo que la query JPQL no sabe ordenar:
        // sin esto Hibernate falla al traducir el HQL y el endpoint da 500.
        pageable.getSort().forEach(order -> {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El campo de ordenación '" + order.getProperty() + "' no es válido");
            }
        });

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

    /** Estados que no permiten editar la cita. */
    private static final Set<String> NON_EDITABLE_STATUSES = Set.of("COMPLETED");

    /** Estados que vuelven a PENDING al editarse. */
    private static final Set<String> RESET_TO_PENDING_ON_EDIT =
            Set.of("CANCELLED", "NO_SHOW");

    /** Actualiza los datos editables de una cita. */
    public AppointmentResponse updateAppointment(Long businessId,
                                                 Long appointmentId,
                                                 UpdateAppointmentRequest request) {

        // 1. Lock pesimista sobre la propia cita (cross-tenant safe).
        Appointment appointment = appointmentRepository
                .findByIdAndBusinessIdForUpdate(appointmentId, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la cita con ID: " + appointmentId
                                + " en el negocio con ID: " + businessId
                ));

        // 2. Estado no editable (solo COMPLETED): rechazo temprano antes
        //    de validar nada mas.
        String currentStatus = appointment.getStatus().getName();
        if (NON_EDITABLE_STATUSES.contains(currentStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se puede editar una cita en estado " + currentStatus
            );
        }

        // 3. Negocio (necesitamos el appointmentInterval).
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el negocio con ID: " + businessId
                ));
        if (!Boolean.TRUE.equals(business.getIsActive())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El negocio con ID: " + businessId + " está desactivado"
            );
        }

        // 4. Cross-tenant + lock pesimista del nuevo empleado.
        Membership membership = membershipRepository
                .findByIdAndBusinessIdForUpdate(request.membershipId(), businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el empleado con ID: " + request.membershipId()
                                + " en el negocio con ID: " + businessId
                ));
        if (!membership.getIsActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El empleado con ID: " + request.membershipId() + " está desactivado"
            );
        }

        // 5. Intervalo del negocio.
        validator.validateAppointmentInterval(
                request.startDateTime(),
                business.getAppointmentInterval()
        );

        // 6. Servicios cross-tenant + activos.
        List<BusinessService> services = new ArrayList<>();
        for (Long serviceId : request.serviceIds()) {
            BusinessService service = serviceRepository
                    .findByIdAndBusinessId(serviceId, businessId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "No se encontró el servicio con ID: " + serviceId
                                    + " en el negocio con ID: " + businessId
                    ));
            if (!service.getIsActive()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El servicio con ID: " + serviceId + " está desactivado"
                );
            }
            services.add(service);
        }

        // 7. endDateTime recalculado segun la duracion actual de los servicios.
        int totalMinutes = services.stream()
                .mapToInt(BusinessService::getDurationMinutes)
                .sum();
        LocalDateTime endDateTime = request.startDateTime().plusMinutes(totalMinutes);

        // 8. Horario de apertura del negocio.
        validator.validateBusinessHours(
                businessId,
                request.startDateTime(),
                endDateTime
        );

        // 9. Horario del empleado.
        validator.validateEmployeeSchedule(
                request.membershipId(),
                request.startDateTime(),
                endDateTime
        );

        // 10. No solape con otra cita del empleado, EXCLUYENDO la propia cita.
        validator.validateNoOverlap(
                request.membershipId(),
                request.startDateTime(),
                endDateTime,
                appointmentId
        );

        // 11. No solape con ausencia del empleado.
        validator.validateNoEmployeeAbsence(
                request.membershipId(),
                request.startDateTime(),
                endDateTime
        );

        // 12. Cabina (si cambia o se mantiene): cross-tenant + lock + activa +
        //     overlap excluyendo la propia cita.
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
                    endDateTime,
                    appointmentId
            );
        }

        // 13. Bloqueos de agenda.
        validator.validateNoScheduleBlock(
                businessId,
                request.membershipId(),
                request.boothId(),
                request.startDateTime()
        );

        // 14. Mutar la cita. createdAt/isPaid/client se conservan.
        //     Si venia de CANCELLED o NO_SHOW, el reagendado equivale a un
        //     ciclo de vida nuevo: el estado vuelve a PENDING para que
        //     pueda transicionar normalmente (CONFIRMED -> IN_PROGRESS -> ...).
        //     En cualquier otro estado activo el estado se conserva.
        if (RESET_TO_PENDING_ON_EDIT.contains(currentStatus)) {
            AppointmentStatus pending = statusRepository.findByName("PENDING")
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "Error de configuración: no se encontró el estado PENDING "
                                    + "en la base de datos."
                    ));
            appointment.setStatus(pending);
        }
        appointment.setMembership(membership);
        appointment.setBooth(booth);
        appointment.setStartDateTime(request.startDateTime());
        appointment.setEndDateTime(endDateTime);
        appointment.setNotes(request.notes());

        // 15. saveAndFlush: si el UPDATE viola uq_appointment_active_slot o
        //     uq_appointment_active_booth_slot (race condition residual a
        //     pesar de los locks), MySQL devuelve la violacion AHORA, no al
        //     commit. Se traduce a 409 con mensaje especifico.
        Appointment saved;
        try {
            saved = appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            if (msg.contains("uq_appointment_active_slot")) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "El empleado ya tiene una cita en ese horario"
                );
            }
            if (msg.contains("uq_appointment_active_booth_slot")) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "La cabina ya tiene una cita en ese horario"
                );
            }
            throw ex;
        }

        // 16. Re-congelar precios: borrar los BookedService actuales y
        //     recrear con los precios e IVA actuales del catalogo. Asi una
        //     cita renegociada refleja el acuerdo del momento de la edicion.
        //     flush() entre el delete y el saveAll para asegurar el orden
        //     SQL: si Hibernate reordenara, podriamos chocar con FKs.
        bookedServiceRepository.deleteAllByAppointmentId(saved.getId());
        bookedServiceRepository.flush();

        List<BookedService> bookedServices = new ArrayList<>();
        for (BusinessService service : services) {
            BookedService booked = new BookedService();
            booked.setAppointment(saved);
            booked.setService(service);
            booked.setAppliedPrice(service.getPrice());
            booked.setAppliedTaxPercentage(service.getTax().getPercentage());
            bookedServices.add(booked);
        }
        List<BookedService> savedBookedServices =
                bookedServiceRepository.saveAll(bookedServices);

        return AppointmentResponse.from(saved, savedBookedServices);
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