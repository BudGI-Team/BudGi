// src/lib/firebase.js
// Firebase Client SDK initialization for real telecom-delivered Phone Authentication

import { initializeApp, getApps, getApp } from 'firebase/app';
import { getAuth, RecaptchaVerifier, signInWithPhoneNumber } from 'firebase/auth';

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || 'AIzaSyAUlLYW2rmOxH1fAF3VTDXC9TysC5ywbto',
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || 'budgi-512209.firebaseapp.com',
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || 'budgi-512209',
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || 'budgi-512209.firebasestorage.app',
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || '257509947874',
  appId: import.meta.env.VITE_FIREBASE_APP_ID || '1:257509947874:web:budgi',
};

const app = getApps().length > 0 ? getApp() : initializeApp(firebaseConfig);
const auth = getAuth(app);

export { auth, RecaptchaVerifier, signInWithPhoneNumber };
