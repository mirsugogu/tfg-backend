const initialAppointments = [
  // ─── Friday May 15 — main demo day (varied durations to show proportionality) ───
  { id: 1, cliente: 'Lucía Fernández',  servicio: 'Corte y peinado',      empleado: 'Marta R.',  fecha: '2026-05-15', hora: '10:30', estado: 'Confirmada', avatar: 'LF', color: 'from-cyan-400 to-blue-500' },
  { id: 2, cliente: 'Carlos Méndez',    servicio: 'Tratamiento facial',   empleado: 'Sofía L.',  fecha: '2026-05-15', hora: '12:00', estado: 'Pendiente',  avatar: 'CM', color: 'from-amber-400 to-orange-500' },
  { id:11, cliente: 'Sara Iglesias',    servicio: 'Coloración',           empleado: 'Marta R.',  fecha: '2026-05-15', hora: '14:00', estado: 'Confirmada', avatar: 'SI', color: 'from-cyan-400 to-blue-500' },
  { id:12, cliente: 'David Olmo',       servicio: 'Manicura premium',     empleado: 'Diego A.',  fecha: '2026-05-15', hora: '16:30', estado: 'Pendiente',  avatar: 'DO', color: 'from-violet-400 to-indigo-500' },

  // ─── Thursday May 16 ───
  { id: 3, cliente: 'Elena Vargas',     servicio: 'Manicura premium',     empleado: 'Diego A.',  fecha: '2026-05-16', hora: '09:15', estado: 'Completada', avatar: 'EV', color: 'from-emerald-400 to-teal-500' },
  { id: 4, cliente: 'Javier Romero',    servicio: 'Masaje relajante',     empleado: 'Pablo T.',  fecha: '2026-05-16', hora: '17:45', estado: 'Confirmada', avatar: 'JR', color: 'from-indigo-400 to-violet-500' },

  // ─── Friday May 17 ───
  { id: 5, cliente: 'Ana Castillo',     servicio: 'Coloración',           empleado: 'Sofía L.',  fecha: '2026-05-17', hora: '11:00', estado: 'Pendiente',  avatar: 'AC', color: 'from-pink-400 to-rose-500' },

  // ─── Friday May 8 — overlap demo (Miguel & Paula nearly back-to-back, Pedro overlaps Miguel) ───
  { id: 6, cliente: 'Miguel Soler',     servicio: 'Corte y peinado',      empleado: 'Diego A.',  fecha: '2026-05-08', hora: '16:30', estado: 'Completada', avatar: 'MS', color: 'from-sky-400 to-blue-500' },
  { id:13, cliente: 'Pedro Vidal',      servicio: 'Tratamiento facial',   empleado: 'Sofía L.',  fecha: '2026-05-08', hora: '16:45', estado: 'Confirmada', avatar: 'PV', color: 'from-blue-400 to-cyan-500' },
  { id: 7, cliente: 'Paula Herrero',    servicio: 'Manicura premium',     empleado: 'Diego A.',  fecha: '2026-05-08', hora: '18:00', estado: 'Confirmada', avatar: 'PH', color: 'from-fuchsia-400 to-pink-500' },

  // ─── Friday May 22 ───
  { id: 8, cliente: 'Rocío Núñez',      servicio: 'Tratamiento facial',   empleado: 'Sofía L.',  fecha: '2026-05-22', hora: '10:00', estado: 'Pendiente',  avatar: 'RN', color: 'from-emerald-400 to-teal-500' },
  { id: 9, cliente: 'Tomás Aguilar',    servicio: 'Masaje relajante',     empleado: 'Pablo T.',  fecha: '2026-05-22', hora: '13:30', estado: 'Confirmada', avatar: 'TA', color: 'from-indigo-400 to-violet-500' },

  // ─── Friday May 28 ───
  { id:10, cliente: 'Sara Iglesias',    servicio: 'Coloración',           empleado: 'Marta R.',  fecha: '2026-05-28', hora: '11:15', estado: 'Confirmada', avatar: 'SI', color: 'from-cyan-400 to-blue-500' },
];

const clientesData = [
  { id: 1, nombre: 'Lucía Fernández',  email: 'lucia@mail.com',  tel: '+34 612 345 678', visitas: 12 },
  { id: 2, nombre: 'Carlos Méndez',    email: 'carlos@mail.com', tel: '+34 645 113 902', visitas: 4  },
  { id: 3, nombre: 'Elena Vargas',     email: 'elena@mail.com',  tel: '+34 678 220 145', visitas: 9  },
  { id: 4, nombre: 'Javier Romero',    email: 'javier@mail.com', tel: '+34 699 884 207', visitas: 2  },
  { id: 5, nombre: 'Ana Castillo',     email: 'ana@mail.com',    tel: '+34 611 902 478', visitas: 7  },
];

const empleadosData = [
  { id: 1, nombre: 'Marta R.',  rol: 'Estilista',  citas: 24 },
  { id: 2, nombre: 'Sofía L.',  rol: 'Esteticista',citas: 19 },
  { id: 3, nombre: 'Diego A.',  rol: 'Manicurista',citas: 15 },
  { id: 4, nombre: 'Pablo T.',  rol: 'Masajista',  citas: 11 },
];

const serviciosData = [
  { id: 1, nombre: 'Corte y peinado',    precio: '€35', duracion: '45 min' },
  { id: 2, nombre: 'Tratamiento facial', precio: '€55', duracion: '60 min' },
  { id: 3, nombre: 'Manicura premium',   precio: '€28', duracion: '40 min' },
  { id: 4, nombre: 'Masaje relajante',   precio: '€60', duracion: '50 min' },
  { id: 5, nombre: 'Coloración',         precio: '€75', duracion: '90 min' },
];

window.OPTIMA_DATA = {
  initialAppointments,
  clientesData,
  empleadosData,
  serviciosData,
};
