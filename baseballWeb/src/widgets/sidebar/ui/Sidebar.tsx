import { NavLink } from "react-router-dom";

import { menuItems } from "../config/menu";

import "./Sidebar.css";

export function Sidebar() {
  return (
    <nav className="sidebar">
      <div className="sidebar__title">Baseball</div>
      <ul className="sidebar__menu">
        {menuItems.map((item) => (
          <li key={item.path}>
            <NavLink
              to={item.path}
              className={({ isActive }) =>
                isActive ? "sidebar__link sidebar__link--active" : "sidebar__link"
              }
            >
              {item.label}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}
