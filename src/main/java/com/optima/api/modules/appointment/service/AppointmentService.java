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

        Client client = clientRepository.findByIdAndBusinessId(
                        request.clientId(), businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el cliente con ID: " + request.clientId()
                                + " en el negocio con ID: " + businessId
                ));

        if (!client.getIsActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El cliente con ID: " + request.clientId() + " está desactivado"
            );
        }

        // Bloqueamos la membership para que dos altas a la vez no pasen el solape.
        Membership membership = membershipRepository.findByIdAndBusinessIdForUpdate(
                        request.membershipId(), businessId)
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

        validator.validateAppointmentInterval(
                request.startDateTime(),
                business.getAppointmentInterval()
        );

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

        // El frontend no manda endDateTime, se calcula con la duracion total.
        int totalMinutes = services.stream()
                .mapToInt(BusinessService::getDurationMinutes)
                .sum();

        LocalDateTime endDateTime = request.startDateTime().plusMinutes(totalMinutes);

        validator.validateBusinessHours(
                businessId,
                request.startDateTime(),
                endDateTime
        );

        validator.validateEmployeeSchedule(
                request.membershipId(),
                request.startDateTime(),
                endDateTime
        );

        validator.validateNoOverlap(
                request.membershipId(),
                request.startDateTime(),
                endDateTime,
                null
        );

        // POST repite esta validacion aunque el calendario ya oculte esos huecos.
        validator.validateNoEmployeeAbsence(
                request.membershipId(),
                request.startDateTime(),
                endDateTime
        );

        // Si hay cabina, tambien se bloquea para evitar dos reservas simultaneas.
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

        validator.validateNoScheduleBlock(
                businessId,
                request.membershipId(),
                request.boothId(),
                request.startDateTime()
        );

        // Si falta PENDING, el problema es de datos base del servidor.
        AppointmentStatus pendingStatus = statusRepository.findByName("PENDING")
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Error de configuración: no se encontró el estado PENDING "
                                + "en la base de datos. Ejecutar los INSERT del schema."
                ));

        Appointment appointment = new Appointment();
        appointment.setBusiness(business);
        appointment.setClient(client);
        appointment.setMembership(membership);
        appointment.setBooth(booth);  // null si la cita no usa cabina
        appointment.setStatus(pendingStatus);
        appointment.setStartDateTime(request.startDateTime());
        appointment.setEndDateTime(endDateTime);
        appointment.setNotes(request.notes());

        // El flush permite convertir los UNIQUE de agenda en un 409 controlado.
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

    /** Busca una cita dentro de un negocio. */
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

        Appointment appointment = appointmentRepository
                .findByIdAndBusinessId(appointmentId, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la cita con ID: " + appointmentId
                                + " en el negocio con ID: " + businessId
                ));

        // Un estado inexistente es entrada invalida, no un recurso del negocio.
        AppointmentStatus newStatus = statusRepository
                .findByName(request.statusName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El estado '" + request.statusName() + "' no es válido. "
                                + "Estados permitidos: PENDING, CONFIRMED, IN_PROGRESS, "
                                + "COMPLETED, CANCELLED, NO_SHOW"
                ));

        String currentStatusName = appointment.getStatus().getName();
        validator.validateStatusTransition(currentStatusName, request.statusName());

        appointment.setStatus(newStatus);

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

        Appointment appointment = appointmentRepository
                .findByIdAndBusinessIdForUpdate(appointmentId, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la cita con ID: " + appointmentId
                                + " en el negocio con ID: " + businessId
                ));

        String currentStatus = appointment.getStatus().getName();
        if (NON_EDITABLE_STATUSES.contains(currentStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se puede editar una cita en estado " + currentStatus
            );
        }

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

        // Se bloquea el empleado nuevo porque puede cambiar al reagendar.
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

        validator.validateAppointmentInterval(
                request.startDateTime(),
                business.getAppointmentInterval()
        );

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

        // Al editar se recalcula la duracion con los servicios actuales.
        int totalMinutes = services.stream()
                .mapToInt(BusinessService::getDurationMinutes)
                .sum();
        LocalDateTime endDateTime = request.startDateTime().plusMinutes(totalMinutes);

        validator.validateBusinessHours(
                businessId,
                request.startDateTime(),
                endDateTime
        );

        validator.validateEmployeeSchedule(
                request.membershipId(),
                request.startDateTime(),
                endDateTime
        );

        validator.validateNoOverlap(
                request.membershipId(),
                request.startDateTime(),
                endDateTime,
                appointmentId
        );

        validator.validateNoEmployeeAbsence(
                request.membershipId(),
                request.startDateTime(),
                endDateTime
        );

        // Si hay cabina, se valida igual que en la creacion pero excluyendo esta cita.
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

        validator.validateNoScheduleBlock(
                businessId,
                request.membershipId(),
                request.boothId(),
                request.startDateTime()
        );

        // Reagendar una cita cancelada la devuelve al flujo normal desde PENDING.
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

        // El flush adelanta posibles choques de UNIQUE para traducirlos a 409.
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

        // Al editar se congelan de nuevo precios e impuestos del catalogo.
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

    /** Cambia el estado de pago de una cita del negocio. */
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
