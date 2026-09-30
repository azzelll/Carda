"use strict";
// Identity-only session in memory. No cookies, local/session storage, telemetry or health input.
const loginForm = document.getElementById("login-form");
const deleteForm = document.getElementById("delete-form");
const email = document.getElementById("email");
const password = document.getElementById("password");
const confirmPassword = document.getElementById("confirm-password");
const confirmDelete = document.getElementById("confirm-delete");
const statusText = document.getElementById("status");
let accessToken = null;
let expiresAt = 0;
let generation = 0;
let activeRequest = null;
let busy = false;
let deleted = false;

function setBusy(value) {
  busy = value;
  document.querySelectorAll("input, button").forEach(control => { control.disabled = value || deleted; });
}
function clearSession() {
  generation++;
  activeRequest?.abort();
  activeRequest = null;
  accessToken = null;
  expiresAt = 0;
  password.value = "";
  confirmPassword.value = "";
  confirmDelete.checked = false;
  deleteForm.hidden = true;
  loginForm.hidden = deleted;
  setBusy(false);
}
async function request(route, method, body, token = null) {
  const controller = new AbortController();
  activeRequest = controller;
  const timeout = setTimeout(() => controller.abort(), 15000);
  try {
    const response = await fetch(`/api/v1/auth${route}`, {
      method, headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: JSON.stringify(body), credentials: "omit", cache: "no-store", redirect: "error", signal: controller.signal,
    });
    let session = null;
    if (route === "/login" && response.ok) {
      const reader = response.body.getReader();
      const chunks = [];
      let bytes = 0;
      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          bytes += value.byteLength;
          if (bytes > 65536) { await reader.cancel(); throw new Error("Session response exceeds budget"); }
          chunks.push(value);
        }
      } finally { reader.releaseLock(); }
      const payload = new Uint8Array(bytes);
      let offset = 0;
      chunks.forEach(chunk => { payload.set(chunk, offset); offset += chunk.byteLength; });
      session = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(payload));
    } else { await response.body?.cancel(); }
    return { status: response.status, ok: response.ok, session };
  } finally {
    clearTimeout(timeout);
    if (activeRequest === controller) activeRequest = null;
  }
}
function errorMessage(code) {
  return code === 429 ? "Terlalu banyak percobaan. Tunggu sebentar lalu coba lagi."
    : "Akun atau kata sandi belum dapat digunakan. Periksa lalu coba lagi.";
}
loginForm.addEventListener("submit", async event => {
  event.preventDefault();
  if (busy || deleted) return;
  const owner = generation;
  setBusy(true);
  statusText.textContent = "Memproses login…";
  try {
    const response = await request("/login", "POST", { email: email.value.trim(), password: password.value });
    if (owner !== generation) return;
    if (!response.ok) { statusText.textContent = errorMessage(response.status); return; }
    const session = response.session;
    if (!session || typeof session.accessToken !== "string" || !/^[\x21-\x7e]{1,512}$/.test(session.accessToken) ||
        typeof session.accountId !== "string" || !/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/.test(session.accountId) ||
        !Number.isInteger(session.expiresInSeconds) || session.expiresInSeconds < 1 || session.expiresInSeconds > 3600) {
      throw new Error("Invalid session response");
    }
    accessToken = session.accessToken;
    expiresAt = Date.now() + session.expiresInSeconds * 1000;
    loginForm.hidden = true;
    deleteForm.hidden = false;
    statusText.textContent = "Login berhasil. Akun belum dihapus; konfirmasi diperlukan.";
  } catch (_) { if (owner === generation) statusText.textContent = "Login belum berhasil. Periksa koneksi lalu coba lagi."; }
  finally { if (owner === generation) { password.value = ""; setBusy(false); if (accessToken) confirmPassword.focus(); } }
});
deleteForm.addEventListener("submit", async event => {
  event.preventDefault();
  if (busy || deleted || !accessToken || !confirmDelete.checked) return;
  if (Date.now() >= expiresAt) {
    clearSession();
    statusText.textContent = "Sesi telah berakhir. Masuk kembali untuk melanjutkan.";
    return;
  }
  const owner = generation;
  setBusy(true);
  statusText.textContent = "Mengirim konfirmasi penghapusan…";
  try {
    const response = await request("/account", "DELETE", { password: confirmPassword.value }, accessToken);
    if (owner !== generation) return;
    if (response.status === 204) {
      deleted = true;
      clearSession();
      loginForm.hidden = true;
      statusText.textContent = "Akun daring dan sesi server dihapus. Bersihkan data lokal pada setiap ponsel secara terpisah.";
    } else if (response.status === 401) {
      clearSession();
      statusText.textContent = "Sesi tidak dapat digunakan lagi. Masuk kembali untuk memeriksa status akun.";
    } else { statusText.textContent = errorMessage(response.status); }
  } catch (_) {
    if (owner === generation) {
      clearSession();
      statusText.textContent = "Status penghapusan belum dapat dipastikan. Periksa koneksi dan coba masuk kembali.";
    }
  } finally { if (owner === generation) { confirmPassword.value = ""; confirmDelete.checked = false; setBusy(false); } }
});
document.getElementById("cancel").addEventListener("click", async () => {
  if (busy || !accessToken) return;
  const token = accessToken;
  clearSession();
  const owner = generation;
  setBusy(true);
  try {
    const response = await request("/logout", "POST", {}, token);
    if (owner !== generation) return;
    statusText.textContent = response.status === 204 ? "Penghapusan dibatalkan. Sesi portal ditutup."
      : "Penghapusan dibatalkan. Pencabutan sesi server belum dapat dipastikan.";
  } catch (_) { if (owner === generation) statusText.textContent = "Penghapusan dibatalkan. Pencabutan sesi server belum dapat dipastikan."; }
  finally { if (owner === generation) setBusy(false); }
});
window.addEventListener("pagehide", () => {
  const pendingDeletion = busy && !deleteForm.hidden;
  clearSession();
  if (!deleted) statusText.textContent = pendingDeletion ? "Status penghapusan belum dapat dipastikan. Coba masuk kembali."
    : "Sesi portal ditutup. Masuk kembali untuk melanjutkan.";
});
window.addEventListener("pageshow", event => { if (event.persisted) clearSession(); });
if (location.protocol !== "https:" && !["localhost", "127.0.0.1", "[::1]"].includes(location.hostname)) {
  deleted = true;
  clearSession();
  setBusy(false);
  statusText.textContent = "Portal akun memerlukan HTTPS. Buka alamat resmi yang disediakan tim Carda.";
}
