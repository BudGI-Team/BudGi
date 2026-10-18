// modules/auth/routes/index.js
'use strict';

const router = require('express').Router();
const auth = require('../../../middleware/auth');
const upload = require('../../../middleware/uploadMiddleware');
const c = require('../controllers/authController');

// Public
router.post('/register', c.register);
router.get('/check-username', c.checkUsername);
router.post('/login', c.login);
router.post('/google', c.googleLogin);
router.post('/refresh', c.refresh);
router.post('/forgot-password', c.forgotPassword);
router.post('/reset-password', c.resetPassword);
router.post('/verify-email', c.verifyEmail);
router.post('/resend-verification', c.resendVerification);

// Protected
router.post('/logout', auth, c.logout);
router.get('/me', auth, c.getMe);
router.put('/profile', auth, c.updateProfile);
router.post('/device-token', auth, c.registerDeviceToken);
router.delete('/device-token', auth, c.removeDeviceToken);
router.delete('/account', auth, c.deleteAccount);
router.delete('/me', auth, c.deleteAccount);
router.post('/upload-profile-picture', auth, upload.single('profileImage'), c.uploadProfilePicture);

module.exports = router;
