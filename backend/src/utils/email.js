const { Resend } = require('resend');

const resend = new Resend(process.env.RESEND_API_KEY);
const FROM_EMAIL = process.env.RESEND_FROM_EMAIL || 'onboarding@resend.dev';

async function sendVerificationEmail(toEmail, toName, verifyUrl) {
  await resend.emails.send({
    from: `Deaf Registry <${FROM_EMAIL}>`,
    to: toEmail,
    subject: 'Verify your Deaf Registry account',
    html: `
      <p>Hi ${toName},</p>
      <p>Thanks for signing up for the Deaf Registry and Monitoring System. Please verify your email address to continue:</p>
      <p><a href="${verifyUrl}">${verifyUrl}</a></p>
      <p>After verifying, an administrator will still need to approve your account before you can log in.</p>
      <p>This link expires in 24 hours.</p>
    `
  });
}

module.exports = { sendVerificationEmail };
