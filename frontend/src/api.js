const BASE = import.meta.env.VITE_API_URL || "http://localhost:8080";

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

const FRIENDLY = {
  400: "That request was not valid. Please check what you entered.",
  401: "Your session has ended. Please log in again.",
  404: "Not found.",
  409: "That email is already registered.",
  413: "That file is too large (the limit is 20 MB).",
  415: "Only PDF, PNG and JPEG files are allowed.",
  502: "The answer service gave an unusable reply. Please try again.",
  503: "The service is temporarily unavailable. Please try again in a moment.",
};

async function request(path, { method = "GET", token, json, form } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;

  let body;
  if (json !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(json);
  } else if (form) {
    body = form; // the browser sets the multipart header itself
  }

  let response;
  try {
    response = await fetch(`${BASE}${path}`, { method, headers, body });
  } catch {
    throw new ApiError(0, "Cannot reach the server. Is the backend running?");
  }

  if (!response.ok) {
    throw new ApiError(response.status, FRIENDLY[response.status] || `Request failed (${response.status})`);
  }
  return response.json();
}

export const register = (name, email, password) =>
  request("/api/auth/register", { method: "POST", json: { name, email, password } });

export const login = (email, password) =>
  request("/api/auth/login", { method: "POST", json: { email, password } });

export const listDocuments = (token) => request("/api/documents", { token });

export function uploadDocument(token, file) {
  const form = new FormData();
  form.append("file", file);
  return request("/api/documents", { method: "POST", token, form });
}

export const reprocessDocument = (token, id) =>
  request(`/api/documents/${id}/reprocess`, { method: "POST", token });

export const ask = (token, question) =>
  request("/api/chat", { method: "POST", token, json: { question } });

export const history = (token, limit = 20) =>
  request(`/api/chat/history?limit=${limit}`, { token });