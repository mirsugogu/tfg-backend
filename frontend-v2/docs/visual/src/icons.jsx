// Stroke icon set — lightweight, consistent 1.6px strokes
const Icon = ({ d, size = 20, className = '', stroke = 'currentColor', fill = 'none', strokeWidth = 1.6, children }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill={fill} stroke={stroke}
       strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" className={className}>
    {d ? <path d={d} /> : children}
  </svg>
);

const Icons = {
  Dashboard: (p) => <Icon {...p}><rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/><rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/></Icon>,
  Users: (p) => <Icon {...p}><circle cx="9" cy="8" r="3.2"/><path d="M3 20c.6-3.2 3.1-5 6-5s5.4 1.8 6 5"/><circle cx="17" cy="9" r="2.4"/><path d="M16 14.2c2.4 0 4.4 1.4 5 4"/></Icon>,
  Employees: (p) => <Icon {...p}><circle cx="12" cy="8" r="3.5"/><path d="M5 20c.7-3.5 3.4-5.5 7-5.5s6.3 2 7 5.5"/><path d="M9 5.5l1.5 -1 1.5 1 1.5-1 1.5 1"/></Icon>,
  Catalog: (p) => <Icon {...p}><path d="M4 5.5C4 4.7 4.7 4 5.5 4H10v16H5.5A1.5 1.5 0 0 1 4 18.5z"/><path d="M14 4h4.5A1.5 1.5 0 0 1 20 5.5v13a1.5 1.5 0 0 1-1.5 1.5H14z"/><path d="M10 4l4 0"/><path d="M10 20l4 0"/></Icon>,
  Calendar: (p) => <Icon {...p}><rect x="3.5" y="4.5" width="17" height="16" rx="2.5"/><path d="M3.5 9.5h17"/><path d="M8 3v3M16 3v3"/></Icon>,
  Settings: (p) => <Icon {...p}><circle cx="12" cy="12" r="2.8"/><path d="M19.4 14.5a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/></Icon>,
  Search: (p) => <Icon {...p}><circle cx="11" cy="11" r="6.5"/><path d="m20 20-3.5-3.5"/></Icon>,
  Bell: (p) => <Icon {...p}><path d="M6 9a6 6 0 1 1 12 0c0 4 1.5 5.5 2 6.5H4c.5-1 2-2.5 2-6.5Z"/><path d="M10 18a2 2 0 0 0 4 0"/></Icon>,
  Plus: (p) => <Icon {...p} d="M12 5v14M5 12h14" />,
  UserPlus: (p) => <Icon {...p}><circle cx="10" cy="8" r="3.5"/><path d="M3 20c.7-3.5 3.4-5.5 7-5.5 1.4 0 2.7.3 3.8.9"/><path d="M18 14v6M15 17h6"/></Icon>,
  Scissors: (p) => <Icon {...p}><circle cx="6" cy="6" r="2.5"/><circle cx="6" cy="18" r="2.5"/><path d="M8.5 7.5 20 18.5"/><path d="M8.5 16.5 20 5.5"/></Icon>,
  Trend: (p) => <Icon {...p}><path d="M4 17l5-5 4 4 7-8"/><path d="M14 8h6v6"/></Icon>,
  Clock: (p) => <Icon {...p}><circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/></Icon>,
  Check: (p) => <Icon {...p} d="M5 12.5l4.5 4.5L19 7.5" />,
  Briefcase: (p) => <Icon {...p}><rect x="3" y="7.5" width="18" height="12.5" rx="2"/><path d="M8.5 7.5V6a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v1.5"/><path d="M3 13h18"/></Icon>,
  Chevron: (p) => <Icon {...p} d="m9 6 6 6-6 6" />,
  Dots: (p) => <Icon {...p}><circle cx="6" cy="12" r="1.3"/><circle cx="12" cy="12" r="1.3"/><circle cx="18" cy="12" r="1.3"/></Icon>,
  Logout: (p) => <Icon {...p}><path d="M14 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2v-2"/><path d="M9 12h12M18 9l3 3-3 3"/></Icon>,
  Close: (p) => <Icon {...p} d="M6 6l12 12M18 6 6 18" />,
};

window.Icons = Icons;
