package com.optima.api.modules.appointment.service;

import com.optima.api.modules.appointment.dto.request.CreateAppointmentRequest;
import com.optima.api.modules.appointment.repository.AppointmentRepository;
import com.optima.api.modules.appointment.repository.AppointmentStatusRepository;
import com.optima.api.modules.appointment.repository.BookedServiceRepository;
import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.model.Membership;
import com.optima.api.modules.business.model.Tax;
import com.optima.api.modules.business.repository.BoothRepository;
import com.optima.api.modules.business.repository.BusinessRepository;
import com.optima.api.modules.business.repository.MembershipRepository;
import com.optima.api.modules.catalog.model.BusinessService;
import com.optima.api.modules.catalog.repository.BusinessServiceRepository;
import com.optima.api.modules.client.model.Client;
import com.optima.api.modules.client.repository.ClientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de AppointmentService.
 *
 * El service tiene 12 validaciones encadenadas en createAppointment. Aqui
 * cubrimos la mas critica: que detecta el solape (overlap) y lanza 409
 * Conflict SIN persistir la cita. El resto de validaciones viven en
 * AppointmentValidator (que aqui mockeamos).
 */
@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private AppointmentStatusRepository statusRepository;
    @Mock private BookedServiceRepository bookedServiceRepository;
    @Mock private BoothRepository boothRepository;
    @Mock private BusinessRepository businessRepository;
    @Mock private BusinessServiceRepository serviceRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private AppointmentValidator validator;

    @InjectMocks private AppointmentService appointmentService;

    @Test
    void createAppointment_lanza409_cuandoEmpleadoTieneOtraCitaSolapada() {
        // --- Arrange: stubs minimos para superar las primeras 6 validaciones ---
        Business business = new Business();
        business.setId(1L);
        business.setAppointmentInterval(30);
        when(businessRepository.findById(1L)).thenReturn(Optional.of(business));

        Client client = new Client();
        client.setId(2L);
        client.setIsActive(true);
        when(clientRepository.findByIdAndBusinessId(2L, 1L)).thenReturn(Optional.of(client));

        Membership employee = new Membership();
        employee.setId(3L);
        employee.setIsActive(true);
        when(membershipRepository.findByIdAndBusinessId(3L, 1L)).thenReturn(Optional.of(employee));

        Tax tax = new Tax();
        tax.setPercentage(new BigDecimal("21.00"));

        BusinessService service = new BusinessService();
        service.setId(4L);
        service.setIsActive(true);
        service.setDurationMinutes(45);
        service.setPrice(new BigDecimal("25.00"));
        service.setTax(tax);
        when(serviceRepository.findByIdAndBusinessId(4L, 1L)).thenReturn(Optional.of(service));

        // El validator de solape lanza el 409 simulando que el repo encontro
        // una cita activa solapada del mismo empleado.
        doThrow(new ResponseStatusException(
                HttpStatus.CONFLICT, "El empleado ya tiene una cita en ese horario"))
                .when(validator).validateNoOverlap(anyLong(), any(), any());

        CreateAppointmentRequest req = new CreateAppointmentRequest(
                2L, 3L,
                null,                 // sin cabina (no relevante para este test)
                LocalDateTime.of(2027, 3, 15, 10, 0),
                "alguna nota",
                List.of(4L)
        );

        // --- Act + Assert ---
        assertThatThrownBy(() -> appointmentService.createAppointment(1L, req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        // La cita NO se persiste si hay solape. Ni la cita ni los bookedServices.
        verify(appointmentRepository, never()).save(any());
        verify(bookedServiceRepository, never()).saveAll(any());
    }

    @Test
    void createAppointment_lanza409_cuandoLaFechaTieneScheduleBlock() {
        // --- Arrange: stubs minimos para superar las primeras validaciones ---
        Business business = new Business();
        business.setId(1L);
        business.setAppointmentInterval(30);
        when(businessRepository.findById(1L)).thenReturn(Optional.of(business));

        Client client = new Client();
        client.setId(2L);
        client.setIsActive(true);
        when(clientRepository.findByIdAndBusinessId(2L, 1L)).thenReturn(Optional.of(client));

        Membership employee = new Membership();
        employee.setId(3L);
        employee.setIsActive(true);
        when(membershipRepository.findByIdAndBusinessId(3L, 1L)).thenReturn(Optional.of(employee));

        Tax tax = new Tax();
        tax.setPercentage(new BigDecimal("21.00"));

        BusinessService service = new BusinessService();
        service.setId(4L);
        service.setIsActive(true);
        service.setDurationMinutes(45);
        service.setPrice(new BigDecimal("25.00"));
        service.setTax(tax);
        when(serviceRepository.findByIdAndBusinessId(4L, 1L)).thenReturn(Optional.of(service));

        // validateNoOverlap y validateNoBoothOverlap pasan (cabina null);
        // validateNoScheduleBlock simula que la fecha cae en "San Isidro" -> 409.
        doThrow(new ResponseStatusException(
                HttpStatus.CONFLICT, "La fecha está bloqueada por: San Isidro"))
                .when(validator).validateNoScheduleBlock(eq(1L), eq(3L), isNull(), any());

        CreateAppointmentRequest req = new CreateAppointmentRequest(
                2L, 3L,
                null,                 // sin cabina
                LocalDateTime.of(2027, 5, 15, 10, 0),  // San Isidro
                "alguna nota",
                List.of(4L)
        );

        // --- Act + Assert ---
        assertThatThrownBy(() -> appointmentService.createAppointment(1L, req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        // La cita NO se persiste si la fecha esta bloqueada.
        verify(appointmentRepository, never()).save(any());
        verify(bookedServiceRepository, never()).saveAll(any());
    }
}
