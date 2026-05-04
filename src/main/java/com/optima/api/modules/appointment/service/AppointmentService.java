package com.optima.api.modules.appointment.service;

import com.optima.api.modules.appointment.dto.request.CreateAppointmentRequest;
import com.optima.api.modules.appointment.dto.request.UpdateAppointmentStatusRequest;
import com.optima.api.modules.appointment.dto.response.AppointmentResponse;
import com.optima.api.modules.appointment.model.Appointment;
import com.optima.api.modules.appointment.model.AppointmentStatus;
import com.optima.api.modules.appointment.model.BookedService;
import com.optima.api.modules.appointment.repository.AppointmentRepository;
import com.optima.api.modules.appointment.repository.AppointmentStatusRepository;
import com.optima.api.modules.appointment.repository.BookedServiceRepository;
import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.repository.BusinessRepository;
import com.optima.api.modules.catalog.model.BusinessService;
import com.optima.api.modules.catalog.repository.BusinessServiceRepository;
import com.optima.api.modules.client.model.Client;
import com.optima.api.modules.client.repository.ClientRepository;
import com.optima.api.modules.user.model.User;
import com.optima.api.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusRepository statusRepository;
    private final BookedServiceRepository bookedServiceRepository;
    private final BusinessRepository businessRepository;
    private final BusinessServiceRepository serviceRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final AppointmentValidator validator;

    /**
     * Crea una nueva cita, validando cross-tenant, horarios,
     * solapamientos e intervalo del negocio.
     * Además crea los BookedService con precios congelados.
     * El endDateTime se calcula automáticamente sumando las duraciones
     * de todos los servicios seleccionados.
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

        // 2b. El cliente debe estar activo (Fix #43)
        if (!client.getIsActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El cliente con ID: " + request.clientId() + " está desactivado"
            );
        }

        // 3. Cross-tenant: el empleado pertenece a este negocio
        User employee = userRepository.findByIdAndBusinessId(
                        request.employeeId(), businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el empleado con ID: " + request.employeeId()
                                + " en el negocio con ID: " + businessId
                ));

        // 3b. El empleado debe estar activo (Fix #42)
        if (!employee.getIsActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El empleado con ID: " + request.employeeId() + " está desactivado"
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

        // 6. Calcular endDateTime sumando las duraciones de los servicios (Fix #50)
        //    El frontend no manda endDateTime, lo calculamos aquí.
        int totalMinutes = services.stream()
                .mapToInt(BusinessService::getDurationMinutes)
                .sum();

        LocalDateTime endDateTime = request.startDateTime().plusMinutes(totalMinutes);

        // 7. Validar que la cita cae dentro del horario del empleado
        //    (ahora sí tenemos endDateTime calculado)
        validator.validateEmployeeSchedule(
                request.employeeId(),
                request.startDateTime(),
                endDateTime
        );

        // 8. Validar que no hay solapamiento con otra cita del empleado
        validator.validateNoOverlap(
                request.employeeId(),
                request.startDateTime(),
                endDateTime
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
        appointment.setEmployee(employee);
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

        bookedServiceRepository.saveAll(bookedServices);

        // 12. Devolver respuesta
        return AppointmentResponse.from(saved);
    }

    /**
     * Lista todas las citas de un negocio.
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByBusiness(Long businessId) {
        return appointmentRepository.findAllByBusinessId(businessId)
                .stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    /**
     * Busca una cita por ID dentro de un negocio (protección cross-tenant).
     */
    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentById(Long id, Long businessId) {
        Appointment appointment = appointmentRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la cita con ID: " + id
                                + " en el negocio con ID: " + businessId
                ));

        return AppointmentResponse.from(appointment);
    }

    /**
     * Cambia el estado de una cita, validando que la transición sea legal.
     */
    public AppointmentResponse updateAppointmentStatus(Long appointmentId,
                                                       Long businessId,
                                                       UpdateAppointmentStatusRequest request) {

        // 1. Buscar la cita (con protección cross-tenant)
        Appointment appointment = appointmentRepository
                .findByIdAndBusinessId(appointmentId, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la cita con ID: " + appointmentId
                                + " en el negocio con ID: " + businessId
                ));

        // 2. Buscar el nuevo estado por nombre
        AppointmentStatus newStatus = statusRepository
                .findByName(request.statusName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el estado: " + request.statusName()
                ));

        // 3. Validar que la transición es permitida
        String currentStatusName = appointment.getStatus().getName();
        validator.validateStatusTransition(currentStatusName, request.statusName());

        // 4. Aplicar el cambio
        appointment.setStatus(newStatus);

        // 5. Guardar y devolver
        Appointment updated = appointmentRepository.save(appointment);
        return AppointmentResponse.from(updated);
    }
}