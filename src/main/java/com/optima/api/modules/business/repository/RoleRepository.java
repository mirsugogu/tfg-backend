package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * RoleRepository - Acceso a la tabla `roles` de MySQL.
 *
 * Interfaz VACIA: no tiene implementacion escrita por nosotros. Spring
 * Data JPA detecta al arrancar las interfaces que extienden JpaRepository
 * y genera una implementacion dinamica en runtime (proxy).
 *
 * Metodos heredados de JpaRepository<Role, Long> ya disponibles:
 *   findAll()        -> SELECT * FROM roles
 *   findById(id)     -> SELECT * FROM roles WHERE id_role = ?
 *   save(entity)     -> INSERT o UPDATE segun haya id
 *   delete(entity), count(), existsById(id), etc.
 *
 * Generico <Role, Long>: entidad Role, clave primaria de tipo Long.
 *
 * COMUNICACION:
 * - Lo inyecta: RoleService (via @RequiredArgsConstructor).
 * - Habla con: MySQL a traves de Hibernate / EntityManager.
 *
 * Si quisieramos un metodo personalizado (p.ej. findByName) bastaria
 * con declararlo aqui: Spring Data deriva la query del nombre del metodo.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {}
