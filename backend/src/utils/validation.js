const MIN_USERNAME_LENGTH = 4;
const MIN_PASSWORD_LENGTH = 8;

function usernameError(username) {
  if (!username || username.trim().length < MIN_USERNAME_LENGTH) {
    return `Username must be at least ${MIN_USERNAME_LENGTH} characters`;
  }
  return null;
}

// Deliberately modest (length + letters + numbers, no symbol requirement) - this app is used by
// field conductors on shared/basic phones, and an overly strict policy creates more support
// burden ("I can't remember my password") than security benefit for this use case.
function passwordError(password) {
  if (!password || password.length < MIN_PASSWORD_LENGTH) {
    return `Password must be at least ${MIN_PASSWORD_LENGTH} characters`;
  }
  if (!/[A-Za-z]/.test(password) || !/[0-9]/.test(password)) {
    return 'Password must include both letters and numbers';
  }
  return null;
}

module.exports = { usernameError, passwordError };
