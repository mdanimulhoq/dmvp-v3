/**
 * @file src/services/authService.js
 * @description DMVP v4.0 — Authentication Service
 * 
 * Handles:
 *   - Email/Password signup & signin
 *   - OTP generation & verification (via Resend)
 *   - Email verification
 *   - Google OAuth
 *   - JWT token management
 */

'use strict';

const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const { Resend } = require('resend');
const { prisma } = require('../config/database');

// Initialize Resend
const resend = new Resend(process.env.RESEND_API_KEY);

// JWT Configuration
const JWT_SECRET = process.env.JWT_SECRET || 'your-secret-key-change-in-production';
const JWT_EXPIRES_IN = process.env.JWT_EXPIRES_IN || '7d';
const REFRESH_TOKEN_EXPIRES_IN = process.env.REFRESH_TOKEN_EXPIRES_IN || '30d';

// OTP Configuration
const OTP_LENGTH = 6;
const OTP_EXPIRY_MINUTES = 10;
const MAX_OTP_ATTEMPTS = 5;

// ═══════════════════════════════════════════════════════
// Helper Functions
// ═══════════════════════════════════════════════════════

/**
 * Generate random OTP
 */
function generateOTP() {
  const digits = '0123456789';
  let otp = '';
  for (let i = 0; i < OTP_LENGTH; i++) {
    otp += digits[Math.floor(Math.random() * 10)];
  }
  return otp;
}

/**
 * Generate email verification token
 */
function generateVerificationToken() {
  return crypto.randomBytes(32).toString('hex');
}

/**
 * Hash password
 */
async function hashPassword(password) {
  const salt = await bcrypt.genSalt(10);
  return bcrypt.hash(password, salt);
}

/**
 * Compare password
 */
async function comparePassword(password, hash) {
  return bcrypt.compare(password, hash);
}

/**
 * Generate JWT token
 */
function generateToken(user) {
  return jwt.sign(
    {
      userId: user.id,
      email: user.email,
      subscriptionTier: user.subscriptionTier,
    },
    JWT_SECRET,
    { expiresIn: JWT_EXPIRES_IN }
  );
}

/**
 * Generate refresh token
 */
function generateRefreshToken(user) {
  return jwt.sign(
    { userId: user.id, type: 'refresh' },
    JWT_SECRET,
    { expiresIn: REFRESH_TOKEN_EXPIRES_IN }
  );
}

/**
 * Verify JWT token
 */
function verifyToken(token) {
  try {
    return jwt.verify(token, JWT_SECRET);
  } catch (error) {
    return null;
  }
}

// ═══════════════════════════════════════════════════════
// Email Functions (Resend)
// ═══════════════════════════════════════════════════════

/**
 * Send OTP email
 */
async function sendOTPEmail(email, otp) {
  try {
    const { data, error } = await resend.emails.send({
      from: 'DMVP <onboarding@resend.dev>', // Change to your verified domain
      to: email,
      subject: 'DMVP - Your Verification Code',
      html: `
        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
          <h2 style="color: #2563eb;">DMVP Verification Code</h2>
          <p>Your one-time verification code is:</p>
          <div style="background: #f3f4f6; padding: 20px; text-align: center; margin: 20px 0;">
            <h1 style="font-size: 32px; letter-spacing: 8px; color: #2563eb; margin: 0;">${otp}</h1>
          </div>
          <p>This code will expire in <strong>${OTP_EXPIRY_MINUTES} minutes</strong>.</p>
          <p style="color: #6b7280; font-size: 14px;">
            If you didn't request this code, please ignore this email.
          </p>
        </div>
      `,
    });

    if (error) {
      console.error('Resend error:', error);
      throw new Error('Failed to send OTP email');
    }

    return data;
  } catch (error) {
    console.error('Send OTP email error:', error);
    throw new Error('Failed to send OTP email');
  }
}

/**
 * Send email verification link
 */
async function sendVerificationEmail(email, token) {
  const verificationUrl = `${process.env.APP_URL || 'https://dmvp-v3-1.onrender.com'}/api/v1/auth/verify-email?token=${token}`;

  try {
    const { data, error } = await resend.emails.send({
      from: 'DMVP <onboarding@resend.dev>',
      to: email,
      subject: 'DMVP - Verify Your Email',
      html: `
        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
          <h2 style="color: #2563eb;">Verify Your Email Address</h2>
          <p>Thank you for signing up for DMVP. Please verify your email address by clicking the button below:</p>
          <div style="text-align: center; margin: 30px 0;">
            <a href="${verificationUrl}" 
               style="background: #2563eb; color: white; padding: 12px 30px; text-decoration: none; border-radius: 6px; font-weight: bold;">
              Verify Email
            </a>
          </div>
          <p style="color: #6b7280; font-size: 14px;">
            Or copy and paste this link into your browser:<br/>
            <span style="color: #2563eb;">${verificationUrl}</span>
          </p>
          <p style="color: #6b7280; font-size: 14px;">
            This link will expire in 24 hours.
          </p>
        </div>
      `,
    });

    if (error) {
      console.error('Resend error:', error);
      throw new Error('Failed to send verification email');
    }

    return data;
  } catch (error) {
    console.error('Send verification email error:', error);
    throw new Error('Failed to send verification email');
  }
}

// ═══════════════════════════════════════════════════════
// Signup / Signin
// ═══════════════════════════════════════════════════════

/**
 * Sign up with email and password
 */
async function signup(email, password, name) {
  // Check if user already exists
  const existingUser = await prisma.user.findUnique({ where: { email } });
  if (existingUser) {
    throw new Error('Email already registered');
  }

  // Hash password
  const passwordHash = await hashPassword(password);

  // Generate verification token
  const emailVerificationToken = generateVerificationToken();

  // Create user
  const user = await prisma.user.create({
    data: {
      email,
      passwordHash,
      name,
      emailVerificationToken,
      emailVerificationSentAt: new Date(),
    },
  });

  // Send verification email
  await sendVerificationEmail(email, emailVerificationToken);

  // Generate tokens
  const token = generateToken(user);
  const refreshToken = generateRefreshToken(user);

  return {
    user: {
      id: user.id,
      email: user.email,
      name: user.name,
      emailVerified: user.emailVerified,
      subscriptionTier: user.subscriptionTier,
    },
    token,
    refreshToken,
  };
}

/**
 * Sign in with email and password
 */
async function signin(email, password) {
  // Find user
  const user = await prisma.user.findUnique({ where: { email } });
  if (!user) {
    throw new Error('Invalid email or password');
  }

  if (!user.isActive) {
    throw new Error('Account is deactivated');
  }

  // Check if user has password (might be Google-only user)
  if (!user.passwordHash) {
    throw new Error('Please sign in with Google');
  }

  // Verify password
  const isValid = await comparePassword(password, user.passwordHash);
  if (!isValid) {
    throw new Error('Invalid email or password');
  }

  // Generate OTP for 2FA (optional - if user wants extra security)
  const otp = generateOTP();
  const otpExpiresAt = new Date(Date.now() + OTP_EXPIRY_MINUTES * 60 * 1000);

  // Update user with OTP
  await prisma.user.update({
    where: { id: user.id },
    data: {
      otpCode: otp,
      otpExpiresAt,
      otpAttempts: 0,
    },
  });

  // Send OTP email
  await sendOTPEmail(email, otp);

  return {
    message: 'OTP sent to your email',
    email: user.email,
    requiresOTP: true,
  };
}

/**
 * Verify OTP and complete signin
 */
async function verifyOTP(email, otp) {
  // Find user
  const user = await prisma.user.findUnique({ where: { email } });
  if (!user) {
    throw new Error('User not found');
  }

  // Check OTP
  if (!user.otpCode || user.otpCode !== otp) {
    // Increment attempts
    await prisma.user.update({
      where: { id: user.id },
      data: { otpAttempts: { increment: 1 } },
    });

    if (user.otpAttempts >= MAX_OTP_ATTEMPTS - 1) {
      // Clear OTP after max attempts
      await prisma.user.update({
        where: { id: user.id },
        data: { otpCode: null, otpExpiresAt: null, otpAttempts: 0 },
      });
      throw new Error('Maximum OTP attempts exceeded. Please request a new OTP.');
    }

    throw new Error('Invalid OTP');
  }

  // Check expiry
  if (user.otpExpiresAt < new Date()) {
    // Clear expired OTP
    await prisma.user.update({
      where: { id: user.id },
      data: { otpCode: null, otpExpiresAt: null },
    });
    throw new Error('OTP has expired. Please request a new one.');
  }

  // Clear OTP
  await prisma.user.update({
    where: { id: user.id },
    data: {
      otpCode: null,
      otpExpiresAt: null,
      otpAttempts: 0,
      lastLoginAt: new Date(),
    },
  });

  // Generate tokens
  const token = generateToken(user);
  const refreshToken = generateRefreshToken(user);

  return {
    user: {
      id: user.id,
      email: user.email,
      name: user.name,
      emailVerified: user.emailVerified,
      subscriptionTier: user.subscriptionTier,
    },
    token,
    refreshToken,
  };
}

/**
 * Verify email with token
 */
async function verifyEmail(token) {
  const user = await prisma.user.findFirst({
    where: { emailVerificationToken: token },
  });

  if (!user) {
    throw new Error('Invalid verification token');
  }

  // Check if token expired (24 hours)
  if (user.emailVerificationSentAt) {
    const sentAt = new Date(user.emailVerificationSentAt);
    const now = new Date();
    const hoursDiff = (now - sentAt) / (1000 * 60 * 60);
    
    if (hoursDiff > 24) {
      throw new Error('Verification token has expired. Please request a new one.');
    }
  }

  // Mark email as verified
  await prisma.user.update({
    where: { id: user.id },
    data: {
      emailVerified: true,
      emailVerificationToken: null,
    },
  });

  return { message: 'Email verified successfully' };
}

/**
 * Resend verification email
 */
async function resendVerificationEmail(email) {
  const user = await prisma.user.findUnique({ where: { email } });
  
  if (!user) {
    throw new Error('User not found');
  }

  if (user.emailVerified) {
    throw new Error('Email already verified');
  }

  // Generate new token
  const emailVerificationToken = generateVerificationToken();

  // Update user
  await prisma.user.update({
    where: { id: user.id },
    data: {
      emailVerificationToken,
      emailVerificationSentAt: new Date(),
    },
  });

  // Send email
  await sendVerificationEmail(email, emailVerificationToken);

  return { message: 'Verification email sent' };
}

/**
 * Resend OTP
 */
async function resendOTP(email) {
  const user = await prisma.user.findUnique({ where: { email } });
  
  if (!user) {
    throw new Error('User not found');
  }

  // Generate new OTP
  const otp = generateOTP();
  const otpExpiresAt = new Date(Date.now() + OTP_EXPIRY_MINUTES * 60 * 1000);

  // Update user
  await prisma.user.update({
    where: { id: user.id },
    data: {
      otpCode: otp,
      otpExpiresAt,
      otpAttempts: 0,
    },
  });

  // Send email
  await sendOTPEmail(email, otp);

  return { message: 'OTP sent to your email' };
}

// ═══════════════════════════════════════════════════════
// Google OAuth
// ═══════════════════════════════════════════════════════

/**
 * Sign in with Google OAuth
 */
async function signinWithGoogle(googleToken) {
  // Verify Google token (in production, use google-auth-library)
  // For now, we'll extract user info from the token
  const googleUser = await verifyGoogleToken(googleToken);

  if (!googleUser) {
    throw new Error('Invalid Google token');
  }

  // Find or create user
  let user = await prisma.user.findUnique({ where: { googleId: googleUser.sub } });

  if (!user) {
    // Check if email already exists
    const existingUser = await prisma.user.findUnique({ where: { email: googleUser.email } });
    
    if (existingUser) {
      // Link Google account to existing user
      user = await prisma.user.update({
        where: { id: existingUser.id },
        data: {
          googleId: googleUser.sub,
          emailVerified: true, // Google already verified
        },
      });
    } else {
      // Create new user
      user = await prisma.user.create({
        data: {
          email: googleUser.email,
          name: googleUser.name,
          googleId: googleUser.sub,
          emailVerified: true, // Google already verified
        },
      });
    }
  }

  if (!user.isActive) {
    throw new Error('Account is deactivated');
  }

  // Update last login
  await prisma.user.update({
    where: { id: user.id },
    data: { lastLoginAt: new Date() },
  });

  // Generate tokens
  const token = generateToken(user);
  const refreshToken = generateRefreshToken(user);

  return {
    user: {
      id: user.id,
      email: user.email,
      name: user.name,
      emailVerified: user.emailVerified,
      subscriptionTier: user.subscriptionTier,
    },
    token,
    refreshToken,
  };
}

/**
 * Verify Google token (simplified - use google-auth-library in production)
 */
async function verifyGoogleToken(token) {
  // In production, use:
  // const { OAuth2Client } = require('google-auth-library');
  // const client = new OAuth2Client(process.env.GOOGLE_CLIENT_ID);
  // const ticket = await client.verifyIdToken({ idToken: token, audience: process.env.GOOGLE_CLIENT_ID });
  // return ticket.getPayload();

  // For now, return null to indicate this needs implementation
  // You'll need to install: npm install google-auth-library
  try {
    const { OAuth2Client } = require('google-auth-library');
    const client = new OAuth2Client(process.env.GOOGLE_CLIENT_ID);
    const ticket = await client.verifyIdToken({
      idToken: token,
      audience: process.env.GOOGLE_CLIENT_ID,
    });
    return ticket.getPayload();
  } catch (error) {
    console.error('Google token verification error:', error);
    return null;
  }
}

// ═══════════════════════════════════════════════════════
// Exports
// ═══════════════════════════════════════════════════════

module.exports = {
  signup,
  signin,
  verifyOTP,
  verifyEmail,
  resendVerificationEmail,
  resendOTP,
  signinWithGoogle,
  verifyToken,
  generateToken,
  generateRefreshToken,
};
