const CACHE_NAME = 'budgi-v1';
const STATIC_ASSETS = ['/', '/index.html', '/favicon.png', '/manifest.json'];

// Install event: cache static assets
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(STATIC_ASSETS);
    }),
  );
  self.skipWaiting();
});

// Activate event: clean up old caches
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(cacheNames.filter((name) => name !== CACHE_NAME).map((name) => caches.delete(name)));
    }),
  );
  self.clients.claim();
});

// Fetch event: network first, fallback to cache for HTML, cache first for assets
self.addEventListener('fetch', (event) => {
  // Only handle GET requests
  if (event.request.method !== 'GET') return;

  // Skip API requests and hot-reload/websocket requests
  if (
    event.request.url.includes('/api/') ||
    event.request.url.includes('/ws') ||
    event.request.url.includes('chrome-extension') ||
    event.request.url.includes('hot-update')
  ) {
    return;
  }

  // Network-first approach for HTML/Navigation requests
  if (event.request.mode === 'navigate') {
    event.respondWith(
      fetch(event.request)
        .catch(() => caches.match('/index.html'))
        .then((response) => response || fetch(event.request)), // Fallback to network if cache fails too
    );
    return;
  }

  // Cache-first approach for static assets
  event.respondWith(
    caches.match(event.request).then((cachedResponse) => {
      if (cachedResponse) return cachedResponse;

      return fetch(event.request)
        .then((networkResponse) => {
          // Cache new assets dynamically
          if (networkResponse && networkResponse.ok) {
            const responseClone = networkResponse.clone();
            caches.open(CACHE_NAME).then((cache) => {
              cache.put(event.request, responseClone);
            });
          }
          return networkResponse;
        })
        .catch(() => {
          // IMPORTANT: NEVER return null or undefined to respondWith
          // If we don't have a cached version and network fails,
          // we should return a basic offline response or just let it fail naturally.
          return new Response('Network error occurred', {
            status: 408,
            statusText: 'Network error',
            headers: new Headers({ 'Content-Type': 'text/plain' }),
          });
        });
    }),
  );
});
