package com.optima.api.modules.appointment.service;

import com.optima.api.modules.appointment.repository.AppointmentRepository;
import com.optima.api.modules.business.repository.BusinessHourRepository;
import com.optima.api.modules.business.repository.ScheduleBlockRepository;
import com.optima.api.modules.user.repository.EmployeeAbsenceRepository;
import com.optima.api.modules.user.repository.EmployeeScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de la maquina de estados de citas (AppointmentValidator.
 * validateStatusTransition). El mapa VALID_TRANSITIONS es logica de
 * dominio central y no estaba cubierto por ningun test.
 *
 * validateStatusTransition no usa repositorios; los @Mock estan solo por
 * el constructor de @RequiredArgsConstructor y no se stubean.
 */
@ExtendWith(MockitoExtension.class)
class AppointmentValidatorTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private EmployeeScheduleRepository scheduleRepository;
    @Mock private EmployeeAbsenceRepository absenceRepository;
    @Mock private ScheduleBlockRepository scheduleBlockRepository;
    @Mock private BusinessHourRepository businessHourRepository;

    @InjectMocks private AppointmentValidator validator;

    @Test
    void validateStatusTransition_noLanza_cuandoLaTransicionEsValida() {
        // PENDING -> CONFIRMED es una transicion permitida.
        assertThatCode(() -> validator.validateStatusTransition("PENDING", "CONFIRMED"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateStatusTransition_lanza400_cuandoLaTransicionEsIlegal() {
        // PENDING -> COMPLETED se salta CONFIRMED/IN_PROGRESS: no permitida.
        assertThatThrownBy(() -> validator.validateStatusTransition("PENDING", "COMPLETED"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void validateStatusTransition_lanza400_cuandoElEstadoActualEsFinal() {
        // COMPLETED es un estado final: no admite ninguna transicion.
        assertThatThrownBy(() -> validator.validateStatusTransition("COMPLETED", "PENDING"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
