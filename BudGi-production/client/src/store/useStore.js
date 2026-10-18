import { create } from 'zustand';
import { persist } from 'zustand/middleware';

const getSystemTheme = () => {
  if (typeof window !== 'undefined' && window.matchMedia) {
    return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
  }
  return 'dark';
};

const useStore = create(
  persist(
    (set, get) => ({
      user: null,
      token: null,
      sidebarOpen: false,
      theme: getSystemTheme(),

      setAuth: (user, token) => {
        set({ user, token });
      },
      updateUser: (user) => {
        set({ user });
      },
      logout: () => {
        set({ user: null, token: null });
      },
      toggleSidebar: () => set((s) => ({ sidebarOpen: !s.sidebarOpen })),
      setSidebarOpen: (open) => set({ sidebarOpen: open }),
      toggleTheme: () =>
        set((s) => {
          const next = s.theme === 'dark' ? 'light' : 'dark';
          document.documentElement.setAttribute('data-theme', next);
          return { theme: next };
        }),
      setTheme: (t) => {
        document.documentElement.setAttribute('data-theme', t);
        set({ theme: t });
      },
      isAuthenticated: () => !!get().token,
    }),
    {
      name: 'budgi-store',
      partialize: (s) => ({ user: s.user, token: s.token, theme: s.theme }),
    },
  ),
);

export default useStore;
