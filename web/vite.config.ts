import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// En local, /ws part vers un relais lancé à côté (RELAY_URL, par défaut localhost:8080).
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/ws": { target: process.env.RELAY_URL ?? "ws://localhost:8080", ws: true },
    },
  },
});
