/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    css: true,
    // O Select/Combobox do Mantine demora ~15-20s a assentar em jsdom (floating-ui
    // sem requestAnimationFrame/IntersectionObserver nativos do browser) — margem
    // generosa em vez de otimizar um ambiente que não é o de produção.
    testTimeout: 60_000,
  },
});
