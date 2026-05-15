package com.optima.api.modules.appointment.controller;

import com.optima.api.common.security.JwtAuthenticationFilter;
import com.optima.api.common.security.TenantGuardFilter;
import com.optima.api.modules.appointment.dto.response.AppointmentResponse;
import com.optima.api.modules.appointment.service.AppointmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest aislado del controller. Levanta SOLO la slice MVC (sin
 * persistencia ni full context), inyecta un AppointmentService mockeado
 * y deshabilita filtros de seguridad (addFilters = false) para probar
 * el contrato del endpoint en aislamiento. La seguridad cross-tenant ya
 * se verifica en TenantGuardFilterTest.
 */
@WebMvcTest(
        controllers = AppointmentController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, TenantGuardFilter.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class AppointmentControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private AppointmentService appointmentService;

    @Test
    void getAppointments_devuelvePaginaConContenido() throws Exception {
        AppointmentResponse cita = new AppointmentResponse(
                100L, 1L,
                2L, "Ana Garcia",
                3L, "Empleado Demo",
                null, null,                 // sin cabina
                1L, "PENDING",
                false,
                LocalDateTime.of(2027, 3, 15, 10, 0),
                LocalDateTime.of(2027, 3, 15, 10, 45),
                "nota de prueba",
                LocalDateTime.now(),
                List.of()
        );
        Page<AppointmentResponse> page = new PageImpl<>(List.of(cita), PageRequest.of(0, 20), 1);

        when(appointmentService.searchAppointments(eq(1L), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/businesses/1/appointments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(100))
                .andExpect(jsonPath("$.content[0].clientName").value("Ana Garcia"))
                .andExpect(jsonPath("$.content[0].statusName").value("PENDING"));
    }
}
