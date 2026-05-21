package com.optima.api.modules.appointment.service;

import com.optima.api.modules.appointment.dto.request.CreateAppointmentRequest;
import com.optima.api.modules.appointment.model.Appointment;
import com.optima.api.modules.appointment.model.AppointmentStatus;
import com.optima.api.modules.appointment.model.BookedService;
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
import com.optima.api.modules.user.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
 * El service tiene una larga cadena de validaciones en createAppointment.
 * Aqui cubrimos el camino feliz (persiste la cita y congela los precios) y
 * los rechazos 409 (solape, ausencia, bloqueo de agenda). El validator se
 * mockea; sus reglas internas se prueban por separado.
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
        when(membershipRepository.findByIdAndBusinessIdForUpdate(3L, 1L)).thenReturn(Optional.of(employee));

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
    void createAppointment_lanza409_cuandoEmpleadoTieneUnaAusenciaSolapada() {
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
        when(membershipRepository.findByIdAndBusinessIdForUpdate(3L, 1L)).thenReturn(Optional.of(employee));

        Tax tax = new Tax();
        tax.setPercentage(new BigDecimal("21.00"));

        BusinessService service = new BusinessService();
        service.setId(4L);
        service.setIsActive(true);
        service.setDurationMinutes(45);
        service.setPrice(new BigDecimal("25.00"));
        service.setTax(tax);
        when(serviceRepository.findByIdAndBusinessId(4L, 1L)).thenReturn(Optional.of(service));

        // validateNoOverlap pasa; validateNoEmployeeAbsence simula que la
        // membership tiene una ausencia 09:00-13:00 que solapa con la cita
        // que se intenta crear (10:00) -> 409.
        doThrow(new ResponseStatusException(
                HttpStatus.CONFLICT, "El empleado tiene una ausencia: Cita medica"))
                .when(validator).validateNoEmployeeAbsence(eq(3L), any(), any());

        CreateAppointmentRequest req = new CreateAppointmentRequest(
                2L, 3L,
                null,                 // sin cabina
                LocalDateTime.of(2027, 3, 17, 10, 0),  // dentro de la ausencia
                "alguna nota",
                List.of(4L)
        );

        // --- Act + Assert ---
        assertThatThrownBy(() -> appointmentService.createAppointment(1L, req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        // La cita NO se persiste si la ausencia bloquea ese horario.
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
        when(membershipRepository.findByIdAndBusinessIdForUpdate(3L, 1L)).thenReturn(Optional.of(employee));

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

    @Test
    void createAppointment_persisteCitaYCongelaPrecios_cuandoTodoEsValido() {
        // --- Arrange: todas las validaciones pasan (validator mockeado no lanza) ---
        Business business = new Business();
        business.setId(1L);
        business.setAppointmentInterval(30);
        when(businessRepository.findById(1L)).thenReturn(Optional.of(business));

        Client client = new Client();
        client.setId(2L);
        client.setIsActive(true);
        when(clientRepository.findByIdAndBusinessId(2L, 1L)).thenReturn(Optional.of(client));

        // La membership necesita su User: AppointmentResponse.from() lee
        // membership.getUser().getFullName() al construir el DTO de salida.
        User user = new User();
        user.setFullName("Empleado Demo");
        Membership employee = new Membership();
        employee.setId(3L);
        employee.setIsActive(true);
        employee.setUser(user);
        when(membershipRepository.findByIdAndBusinessIdForUpdate(3L, 1L))
                .thenReturn(Optional.of(employee));

        Tax tax = new Tax();
        tax.setPercentage(new BigDecimal("21.00"));

        BusinessService service = new BusinessService();
        service.setId(4L);
        service.setName("Corte de pelo");
        service.setIsActive(true);
        service.setDurationMinutes(45);
        service.setPrice(new BigDecimal("25.00"));
        service.setTax(tax);
        when(serviceRepository.findByIdAndBusinessId(4L, 1L)).thenReturn(Optional.of(service));

        // El estado PENDING debe existir en BD (paso 9 de createAppointment).
        AppointmentStatus pending = new AppointmentStatus();
        pending.setId(1L);
        pending.setName("PENDING");
        when(statusRepository.findByName("PENDING")).thenReturn(Optional.of(pending));

        // saveAndFlush / saveAll devuelven lo que reciben (no hay BD real).
        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(bookedServiceRepository.saveAll(anyList()))
                .thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime start = LocalDateTime.of(2027, 3, 15, 10, 0);
        CreateAppointmentRequest req = new CreateAppointmentRequest(
                2L, 3L, null, start, "alguna nota", List.of(4L));

        // --- Act ---
        appointmentService.createAppointment(1L, req);

        // --- Assert: la cita se persiste con endDateTime = inicio + 45 min ---
        ArgumentCaptor<Appointment> apptCaptor = ArgumentCaptor.captor();
        verify(appointmentRepository).saveAndFlush(apptCaptor.capture());
        Appointment savedAppt = apptCaptor.getValue();
        assertThat(savedAppt.getStartDateTime()).isEqualTo(start);
        assertThat(savedAppt.getEndDateTime()).isEqualTo(start.plusMinutes(45));
        assertThat(savedAppt.getStatus()).isSameAs(pending);
        assertThat(savedAppt.getBooth()).isNull();

        // --- Assert: el BookedService congela el precio y el % de impuesto ---
        ArgumentCaptor<List<BookedService>> bookedCaptor = ArgumentCaptor.captor();
        verify(bookedServiceRepository).saveAll(bookedCaptor.capture());
        List<BookedService> booked = bookedCaptor.getValue();
        assertThat(booked).hasSize(1);
        assertThat(booked.get(0).getAppliedPrice()).isEqualByComparingTo("25.00");
        assertThat(booked.get(0).getAppliedTaxPercentage()).isEqualByComparingTo("21.00");
    }

    @Test
    void searchAppointments_lanza400_cuandoElSortNoEstaEnLaWhitelist() {
        PageRequest conSortInvalido = PageRequest.of(0, 20, Sort.by("campoQueNoExiste"));

        assertThatThrownBy(() ->
                appointmentService.searchAppointments(1L, null, null, null, conSortInvalido))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // El sort se valida ANTES de tocar el repositorio.
        verify(appointmentRepository, never())
                .searchAppointments(any(), any(), any(), any(), any());
    }
}
