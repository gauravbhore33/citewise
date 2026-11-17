import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// strictPort: the backend only allows requests from port 5173, so never switch to another port
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true },
});