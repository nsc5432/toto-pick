export interface MenuItem {
  label: string;
  path: string;
}

export const menuItems: MenuItem[] = [
  { label: "경기결과 조회", path: "/baseball-results" },
  { label: "알고리즘 비교 대시보드", path: "/pick-results" },
  { label: "최적 알고리즘 탐색", path: "/research" },
  { label: "관리자", path: "/admin" },
];
