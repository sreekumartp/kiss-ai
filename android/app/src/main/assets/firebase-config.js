// Sign-in and progress sync (Firebase).
// Leave this as null to keep the app fully local. To turn sync on, replace null with
// your Firebase web app config (Project settings -> General -> Your apps -> Web app).
// These values are public by design; access is controlled by firestore.rules.
window.KISS_FIREBASE_CONFIG = null;

/* Example:
window.KISS_FIREBASE_CONFIG = {
  apiKey: "AIza...",
  authDomain: "your-project.firebaseapp.com",
  projectId: "your-project",
  appId: "1:1234567890:web:abc123"
};
*/
