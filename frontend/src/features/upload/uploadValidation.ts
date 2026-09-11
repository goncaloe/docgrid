/**
 * Espelha `docgrid.upload` em `application.yml`. Não há endpoint para consultar a
 * política do servidor; se ela mudar lá, atualiza-se aqui também. A validação real e
 * definitiva continua a ser feita pelo backend em `/api/documents/upload-url`.
 */
export const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

export const ALLOWED_CONTENT_TYPES: Record<string, string> = {
  "application/pdf": "pdf",
  "image/jpeg": "jpg",
  "image/png": "png",
};

export function validateFile(file: File): string | null {
  if (!(file.type in ALLOWED_CONTENT_TYPES)) {
    return "Tipo de ficheiro não suportado. Usa PDF, JPEG ou PNG.";
  }
  if (file.size > MAX_FILE_SIZE_BYTES) {
    return "Ficheiro demasiado grande (máximo 10 MB).";
  }
  return null;
}
