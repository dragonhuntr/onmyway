import { bindings, defineConfig } from "cf/config";
import * as entrypoint from "./src/index.ts" with { type: "cf-worker" };

export default defineConfig({
  worker: {
    name: "onmyway-api",
    entrypoint,
    compatibilityDate: "2026-10-04",
    env: {
      DB: bindings.d1({
        name: "onmyway",
        id: "2e489ff3-7c39-4481-a04e-72533217a363",
      }),
    },
  },
});
