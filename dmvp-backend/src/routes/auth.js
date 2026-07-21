/**
 * @file src/routes/auth.js
 * @description DMVP v4.0 — Authentication Routes
 * 
 * Endpoints:
 *   POST /auth/signup              — Sign up with email/password
 *   POST /auth/signin              — Sign in (sends OTP)
 *   POST /auth/verify-otp          — Verify OTP and complete signin
 *   POST /auth/resend-otp          — Resend OTP
 *   POST /auth/verify-email        — Verify email with token
 *   POST /auth/resend-verification — Resend verification email
 *   POST /auth/google              — Sign in with Google OAuth
 *   GET  /auth/me                  — Get current user
 */

'use strict';

const express = require('express');
const router = express.Router();
const authService = require('../services/authService');
const { authenticate } = require('../middleware/auth');
const { rateLimiter } = require('../middleware/rateLimit');
const { prisma } = require('../config/database');

// ═══════════════════════════════════════════════════════
// Signup
// ═══════════════════════════════════════════════════════

/**
 * POST /auth/signup
 * Sign up with email and password
 */
router.post('/signup', rateLimiter({ windowMs: 15 * 60 * 1000, max: 10 }), async (req, res) => {
  try {
    const { email, password, name } = req.body;

    // Validation
    if (!email || !password) {
      return res.status(400).json({
        success: false,
        error: 'Email and password are required',
      });
    }

    if (password.length < 8) {
      return res.status(400).json({
        success: false,
        error: 'Password must be at least 8 characters',
      });
    }

    // Email format validation
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      return res.status(400).json({
        success: false,
        error: 'Invalid email format',
      });
    }

    // Signup
    const result = await authService.signup(email, password, name);

    return res.status(201).json({
      success: true,
      message: 'Signup successful. Please check your email to verify your account.',
      ...result,
    });
  } catch (error) {
    console.error('Signup error:', error);

    if (error.message === 'Email already registered') {
      return res.status(409).json({
        success: false,
        error: error.message,
      });
    }

    return res.status(500).json({
      success: false,
      error: 'Signup failed. Please try again.',
      details: process.env.NODE_ENV === 'development' ? error.message : undefined,
    });
  }
});

// ═══════════════════════════════════════════════════════
// Signin
// ═══════════════════════════════════════════════════════

/**
 * POST /auth/signin
 * Sign in with email and password (sends OTP)
 */
router.post('/signin', rateLimiter({ windowMs: 15 * 60 * 1000, max: 20 }), async (req, res) => {
  try {
    const { email, password } = req.body;

    // Validation
    if (!email || !password) {
      return res.status(400).json({
        success: false,
        error: 'Email and password are required',
      });
    }

    // Signin (sends OTP)
    const result = await authService.signin(email, password);

    return res.status(200).json({
      success: true,
      ...result,
    });
  } catch (error) {
    console.error('Signin error:', error);

    if (error.message === 'Invalid email or password') {
      return res.status(401).json({
        success: false,
        error: error.message,
      });
    }

    if (error.message === 'Account is deactivated') {
      return res.status(403).json({
        success: false,
        error: error.message,
      });
    }

    if (error.message === 'Please sign in with Google') {
      return res.status(400).json({
        success: false,
        error: error.message,
        requiresGoogle: true,
      });
    }

    return res.status(500).json({
      success: false,
      error: 'Signin failed. Please try again.',
      details: process.env.NODE_ENV === 'development' ? error.message : undefined,
    });
  }
});

/**
 * POST /auth/verify-otp
 * Verify OTP and complete signin
 */
router.post('/verify-otp', rateLimiter({ windowMs: 15 * 60 * 1000, max: 20 }), async (req, res) => {
  try {
    const { email, otp } = req.body;

    // Validation
    if (!email || !otp) {
      return res.status(400).json({
        success: false,
        error: 'Email and OTP are required',
      });
    }

    // Verify OTP
    const result = await authService.verifyOTP(email, otp);

    return res.status(200).json({
      success: true,
      message: 'OTP verified successfully',
      ...result,
    });
  } catch (error) {
    console.error('Verify OTP error:', error);

    if (error.message.includes('Invalid OTP') || error.message.includes('expired')) {
      return res.status(400).json({
        success: false,
        error: error.message,
      });
    }

    if (error.message.includes('Maximum OTP attempts')) {
      return res.status(429).json({
        success: false,
        error: error.message,
      });
    }

    return res.status(500).json({
      success: false,
      error: 'OTP verification failed. Please try again.',
    });
  }
});

/**
 * POST /auth/resend-otp
 * Resend OTP
 */
router.post('/resend-otp', rateLimiter({ windowMs: 15 * 60 * 1000, max: 5 }), async (req, res) => {
  try {
    const { email } = req.body;

    if (!email) {
      return res.status(400).json({
        success: false,
        error: 'Email is required',
      });
    }

    const result = await authService.resendOTP(email);

    return res.status(200).json({
      success: true,
      ...result,
    });
  } catch (error) {
    console.error('Resend OTP error:', error);
    return res.status(500).json({
      success: false,
      error: 'Failed to resend OTP',
    });
  }
});

// ═══════════════════════════════════════════════════════
// Email Verification
// ═══════════════════════════════════════════════════════

/**
 * POST /auth/verify-email
 * Verify email with token
 */
router.post('/verify-email', async (req, res) => {
  try {
    const { token } = req.body;

    if (!token) {
      return res.status(400).json({
        success: false,
        error: 'Verification token is required',
      });
    }

    const result = await authService.verifyEmail(token);

    return res.status(200).json({
      success: true,
      ...result,
    });
  } catch (error) {
    console.error('Verify email error:', error);

    if (error.message.includes('Invalid') || error.message.includes('expired')) {
      return res.status(400).json({
        success: false,
        error: error.message,
      });
    }

    return res.status(500).json({
      success: false,
      error: 'Email verification failed',
    });
  }
});

/**
 * GET /auth/verify-email
 * Verify email via link click (redirect to app)
 */
router.get('/verify-email', async (req, res) => {
  try {
    const { token } = req.query;

    if (!token) {
      return res.status(400).send('Invalid verification link');
    }

    await authService.verifyEmail(token);

    // Redirect to app with success message
    // In production, this should redirect to your app's verification success page
    return res.send(`
      <!DOCTYPE html>
      <html>
        <head>
          <title>Email Verified - DMVP</title>
          <style>
            body { font-family: Arial, sans-serif; text-align: center; padding: 50px; }
            .success { color: #10b981; font-size: 24px; margin-bottom: 20px; }
            .message { color: #6b7280; }
          </style>
        </head>
        <body>
          <h1 class="success">✓ Email Verified Successfully!</h1>
          <p class="message">You can now close this window and sign in to DMVP.</p>
        </body>
      </html>
    `);
  } catch (error) {
    console.error('Verify email error:', error);
    return res.status(400).send(`
      <!DOCTYPE html>
      <html>
        <head>
          <title>Verification Failed - DMVP</title>
          <style>
            body { font-family: Arial, sans-serif; text-align: center; padding: 50px; }
            .error { color: #ef4444; font-size: 24px; margin-bottom: 20px; }
            .message { color: #6b7280; }
          </style>
        </head>
        <body>
          <h1 class="error">✗ Verification Failed</h1>
          <p class="message">${error.message}</p>
        </body>
      </html>
    `);
  }
});

/**
 * POST /auth/resend-verification
 * Resend verification email
 */
router.post('/resend-verification', rateLimiter({ windowMs: 15 * 60 * 1000, max: 5 }), async (req, res) => {
  try {
    const { email } = req.body;

    if (!email) {
      return res.status(400).json({
        success: false,
        error: 'Email is required',
      });
    }

    const result = await authService.resendVerificationEmail(email);

    return res.status(200).json({
      success: true,
      ...result,
    });
  } catch (error) {
    console.error('Resend verification error:', error);

    if (error.message.includes('not found') || error.message.includes('already verified')) {
      return res.status(400).json({
        success: false,
        error: error.message,
      });
    }

    return res.status(500).json({
      success: false,
      error: 'Failed to resend verification email',
    });
  }
});

// ═══════════════════════════════════════════════════════
// Google OAuth
// ═══════════════════════════════════════════════════════

/**
 * POST /auth/google
 * Sign in with Google OAuth
 */
router.post('/google', rateLimiter({ windowMs: 15 * 60 * 1000, max: 20 }), async (req, res) => {
  try {
    const { googleToken } = req.body;

    if (!googleToken) {
      return res.status(400).json({
        success: false,
        error: 'Google token is required',
      });
    }

    const result = await authService.signinWithGoogle(googleToken);

    return res.status(200).json({
      success: true,
      message: 'Google signin successful',
      ...result,
    });
  } catch (error) {
    console.error('Google signin error:', error);

    if (error.message === 'Invalid Google token') {
      return res.status(401).json({
        success: false,
        error: error.message,
      });
    }

    if (error.message === 'Account is deactivated') {
      return res.status(403).json({
        success: false,
        error: error.message,
      });
    }

    return res.status(500).json({
      success: false,
      error: 'Google signin failed. Please try again.',
    });
  }
});

// ═══════════════════════════════════════════════════════
// Token Refresh
// ═══════════════════════════════════════════════════════

/**
 * POST /auth/refresh-token
 * Exchange a valid refresh token for a new access token
 */
router.post('/refresh-token', rateLimiter({ windowMs: 15 * 60 * 1000, max: 30 }), async (req, res) => {
  try {
    const { refreshToken } = req.body;

    if (!refreshToken) {
      return res.status(400).json({
        success: false,
        error: 'Refresh token is required',
      });
    }

    // Verify refresh token
    const decoded = authService.verifyToken(refreshToken);

    if (!decoded || decoded.type !== 'refresh') {
      return res.status(401).json({
        success: false,
        error: 'Invalid or expired refresh token',
      });
    }

    // Find user
    const user = await prisma.user.findUnique({ where: { id: decoded.sub } });
    if (!user || !user.isActive) {
      return res.status(401).json({
        success: false,
        error: 'User not found or account is deactivated',
      });
    }

    // Generate new tokens
    const token = authService.generateToken(user);
    const newRefreshToken = authService.generateRefreshToken(user);

    return res.status(200).json({
      success: true,
      token,
      refreshToken: newRefreshToken,
    });
  } catch (error) {
    console.error('Refresh token error:', error);
    return res.status(500).json({
      success: false,
      error: 'Token refresh failed',
    });
  }
});

// ═══════════════════════════════════════════════════════
// Protected Routes
// ═══════════════════════════════════════════════════════

/**
 * GET /auth/me
 * Get current user info
 */
router.get('/me', authenticate, async (req, res) => {
  try {
    const user = await prisma.user.findUnique({
      where: { id: req.user.accountId },
      select: {
        id: true,
        email: true,
        name: true,
        emailVerified: true,
        subscriptionTier: true,
        zkProofsUsed: true,
        zkProofsLimit: true,
        createdAt: true,
        lastLoginAt: true,
      },
    });

    if (!user) {
      return res.status(404).json({
        success: false,
        error: 'User not found',
      });
    }

    return res.status(200).json({
      success: true,
      user,
    });
  } catch (error) {
    console.error('Get user error:', error);
    return res.status(500).json({
      success: false,
      error: 'Failed to get user info',
    });
  }
});

module.exports = router;
