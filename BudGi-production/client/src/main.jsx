import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from 'react-hot-toast';
import App from './App.jsx';
import './index.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
      refetchOnWindowFocus: false,
    },
  },
});

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter
        future={{
          v7_startTransition: true,
          v7_relativeSplatPath: true,
          v7_fetcherPersist: true,
          v7_normalizeFormMethod: true,
          v7_partialHydration: true,
          v7_skipActionErrorRevalidation: true,
        }}
      >
        <App />
      </BrowserRouter>
      <Toaster
        position="top-right"
        containerStyle={{ top: 72 }}
        toastOptions={{
          duration: 3000,
          style: {
            background: 'var(--bg-elevated)',
            color: 'var(--text-primary)',
            border: '1px solid var(--border-strong)',
            borderRadius: '14px',
            fontFamily: 'var(--font-body)',
            fontSize: '14px',
            padding: '12px 16px',
            boxShadow: '0 8px 32px rgba(0,0,0,0.25)',
            maxWidth: '380px',
          },
          success: {
            iconTheme: { primary: 'var(--green)', secondary: 'var(--bg-elevated)' },
            duration: 2500,
          },
          error: {
            iconTheme: { primary: 'var(--red)', secondary: 'var(--bg-elevated)' },
            duration: 4000,
          },
        }}
        gutter={8}
      />
    </QueryClientProvider>
  </React.StrictMode>,
);
