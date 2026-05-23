package com.optima.api.modules.appointment.service;

import com.optima.api.modules.appointment.dto.response.AvailabilityResponse;
import com.optima.api.modules.appointment.repository.AppointmentRepository;
import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.model.BusinessHour;
import com.optima.api.modules.business.repository.BoothRepository;
import com.optima.api.modules.business.repository.BusinessHourRepository;
import com.optima.api.modules.business.repository.BusinessRepository;
import com.optima.api.modules.business.repository.MembershipRepository;
import com.optima.api.modules.business.repository.ScheduleBlockRepository;
import com.optima.api.modules.catalog.model.BusinessService;
import com.optima.api.modules.catalog.repository.BusinessServiceRepository;
import com.optima.api.modules.user.repository.EmployeeAbsenceRepository;
import com.optima.api.modules.user.repository.EmployeeScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de AvailabilityService.
 *
 * El algoritmo cruza 8 fuentes de datos; aqui cubrimos el caso defensible
 * mas comun: el negocio esta cerrado ese dia (business_hours.is_closed=true)
 * -> la lista de slots debe venir vacia.
 *
 * Casos mas complejos (slots reales, interseccion con citas/absences) se
 * validan end-to-end via Docker + Swagger.
 */
@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    @Mock private BusinessRepository businessRepository;
    @Mock private BusinessHourRepository businessHourRepository;
    @Mock private BusinessServiceRepository serviceRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private EmployeeScheduleRepository scheduleRepository;
    @Mock private EmployeeAbsenceRepository absenceRepository;
    @Mock private BoothRepository boothRepository;
    @Mock private ScheduleBlockRepository scheduleBlockRepository;
    @Mock private AppointmentRepository appointmentRepository;

    @InjectMocks private AvailabilityService availabilityService;

    @Test
    void getAvailability_devuelveSlotsVacios_cuandoElNegocioEstaCerradoEseDia() {
        Long businessId = 1L;
        LocalDate date = LocalDate.of(2027, 3, 21); // domingo
        List<Long> serviceIds = List.of(1L);

        Business business = new Business();
        business.setId(businessId);
        business.setAppointmentInterval(30);
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

        BusinessService service = new BusinessService();
        service.setId(1L);
        service.setIsActive(true);
        service.setDurationMinutes(30);
        when(serviceRepository.findAllByIdInAndBusinessId(java.util.Set.of(1L), businessId))
                .thenReturn(java.util.List.of(service));

        // El domingo el negocio esta cerrado: is_closed=true, sin horas.
        BusinessHour sunday = new BusinessHour();
        sunday.setDayOfWeek(7);
        sunday.setIsClosed(true);
        sunday.setStartTime(LocalTime.of(0, 0));
        sunday.setEndTime(LocalTime.of(0, 0));
        when(businessHourRepository.findAllByBusinessIdAndDayOfWeekOrderByStartTimeAsc(businessId, 7))
                .thenReturn(java.util.List.of(sunday));

        AvailabilityResponse response = availabilityService.getAvailability(
                businessId, date, serviceIds, null, null, null);

        assertThat(response.date()).isEqualTo(date);
        assertThat(response.businessId()).isEqualTo(businessId);
        assertThat(response.totalDurationMinutes()).isEqualTo(30);
        assertThat(response.slots()).isEmpty();
    }
}
