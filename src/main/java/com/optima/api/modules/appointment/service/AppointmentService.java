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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
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
    @Transactional
    public AppointmentResponse createAppointment(CreateAppointmentRequest request) {

        // 1. Buscar el negocio (necesitamos el appointmentInterval)
        Business business = businessRepository.findById(request.businessId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontró el negocio con ID: " + request.businessId()
                ));

        // 2. Cross-tenant: el cliente pertenece a este negocio
        Client client = clientRepository.findByIdAndBusinessId(
                        request.clientId(), request.businessId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontró el cliente con ID: " + request.clientId()
                                + " en el negocio con ID: " + request.businessId()
                ));

        // 2b. El cliente debe estar activo (Fix #43)
        if (!client.getIsActive()) {
            throw new IllegalArgumentException(
                    "El cliente con ID: " + request.clientId() + " está desactivado."
            );
        }

        // 3. Cross-tenant: el empleado pertenece a este negocio
        User employee = userRepository.findByIdAndBusinessId(
                        request.employeeId(), request.businessId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontró el empleado con ID: " + request.employeeId()
                                + " en el negocio con ID: " + request.businessId()
                ));

        // 3b. El empleado debe estar activo (Fix #42)
        if (!employee.getIsActive()) {
            throw new IllegalArgumentException(
                    "El empleado con ID: " + request.employeeId() + " está desactivado."
            );
        }

        // 4. Validar que la hora respeta el intervalo del negocio
        //    (solo necesita startDateTime, no depende de endDateTime)
        validator.validateAppointmentInterval(
                request.startDateTime(),
                business.getAppointmentInterval()
        );

        // 5. Buscar los servicios y validar cada uno
        List<BusinessService> services = new ArrayList<>();
        for (Long serviceId : request.serviceIds()) {
            BusinessService service = serviceRepository.findById(serviceId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No se encontró el servicio con ID: " + serviceId
                    ));

            // Cross-tenant: el servicio pertenece a este negocio
            if (!service.getBusiness().getId().equals(request.businessId())) {
                throw new IllegalArgumentException(
                        "El servicio con ID: " + serviceId
                                + " no pertenece al negocio con ID: " + request.businessId()
                );
            }

            // El servicio debe estar activo
            if (!service.getIsActive()) {
                throw new IllegalArgumentException(
                        "El servicio con ID: " + serviceId + " está desactivado."
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

        // 9. Buscar el estado PENDING (Fix #49: IllegalStateException en vez de
        //    IllegalArgumentException, porque es un error del servidor, no del usuario)
        AppointmentStatus pendingStatus = statusRepository.findByName("PENDING")
                .orElseThrow(() -> new IllegalStateException(
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
        return toResponse(saved);
    }

    /**
     * Lista todas las citas de un negocio.
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByBusiness(Long businessId) {
        return appointmentRepository.findAllByBusinessId(businessId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Busca una cita por ID dentro de un negocio (protección cross-tenant).
     */
    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentById(Long id, Long businessId) {
        Appointment appointment = appointmentRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontró la cita con ID: " + id
                                + " en el negocio con ID: " + businessId
                ));

        return toResponse(appointment);
    }

    /**
     * Cambia el estado de una cita, validando que la transición sea legal.
     */
    @Transactional
    public AppointmentResponse updateAppointmentStatus(Long appointmentId,
                                                       Long businessId,
                                                       UpdateAppointmentStatusRequest request) {

        // 1. Buscar la cita (con protección cross-tenant)
        Appointment appointment = appointmentRepository
                .findByIdAndBusinessId(appointmentId, businessId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontró la cita con ID: " + appointmentId
                                + " en el negocio con ID: " + businessId
                ));

        // 2. Buscar el nuevo estado por nombre
        AppointmentStatus newStatus = statusRepository
                .findByName(request.statusName())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el estado: " + request.statusName()
                ));

        // 3. Validar que la transición es permitida
        String currentStatusName = appointment.getStatus().getName();
        validator.validateStatusTransition(currentStatusName, request.statusName());

        // 4. Aplicar el cambio
        appointment.setStatus(newStatus);

        // 5. Guardar y devolver
        Appointment updated = appointmentRepository.save(appointment);
        return toResponse(updated);
    }

    /**
     * Convierte la entidad Appointment al DTO de respuesta.
     * Incluye los nombres de cliente, empleado y estado para que
     * el frontend no tenga que hacer llamadas extra.
     */
    private AppointmentResponse toResponse(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getBusiness().getId(),
                appointment.getClient().getId(),
                appointment.getClient().getFullName(),
                appointment.getEmployee().getId(),
                appointment.getEmployee().getFullName(),
                appointment.getStatus().getId(),
                appointment.getStatus().getName(),
                appointment.getIsPaid(),
                appointment.getStartDateTime(),
                appointment.getEndDateTime(),
                appointment.getNotes(),
                appointment.getCreatedAt()
        );
    }
}