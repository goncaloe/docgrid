#!/usr/bin/env node
// Reencaminha os argumentos para o Maven Wrapper em `backend/`, escolhendo o
// executável certo para a plataforma. Existe porque `./mvnw` não corre em
// cmd.exe e `mvnw` não está no PATH em Linux e macOS — sem isto, cada script
// do package.json teria de ter uma variante por sistema operativo.
import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const backend = join(root, "backend");
const isWindows = process.platform === "win32";

// O .env não é versionado; quando existe, alimenta o perfil `local`.
const envFile = join(root, ".env");
if (existsSync(envFile)) {
    process.loadEnvFile(envFile);
}

const wrapper = join(backend, isWindows ? "mvnw.cmd" : "mvnw");
const result = spawnSync(wrapper, process.argv.slice(2), {
    cwd: backend,
    stdio: "inherit",
    shell: isWindows,
});

if (result.error) {
    console.error(`Não foi possível executar o Maven Wrapper (${wrapper}): ${result.error.message}`);
    process.exit(1);
}

process.exit(result.status ?? 1);
