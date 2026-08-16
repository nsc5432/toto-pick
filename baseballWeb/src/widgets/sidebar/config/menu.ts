export interface MenuItem {
  label: string;
  path: string;
}

export const menuItems: MenuItem[] = [
  { label: "경기결과 조회", path: "/baseball-results" },
  { label: "시뮬레이션 결과 조회", path: "/pick-results" },
  { label: "관리자", path: "/admin" },
];
