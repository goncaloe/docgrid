import { Group, Paper, Progress, Stack, Text, Title } from "@mantine/core";
import { Dropzone, MIME_TYPES } from "@mantine/dropzone";
import { IconCheck, IconFileUpload, IconUpload, IconX } from "@tabler/icons-react";

import { MAX_FILE_SIZE_BYTES } from "./uploadValidation";
import { useDocumentUpload } from "./useDocumentUpload";

const ACCEPTED_TYPES = [MIME_TYPES.pdf, MIME_TYPES.jpeg, MIME_TYPES.png];

export function UploadPage() {
  const { items, uploadFiles, rejectFiles } = useDocumentUpload();

  return (
    <Stack gap="md">
      <Title order={2}>Submeter documentos</Title>
      <Dropzone
        onDrop={uploadFiles}
        onReject={rejectFiles}
        accept={ACCEPTED_TYPES}
        maxSize={MAX_FILE_SIZE_BYTES}
      >
        <Group justify="center" gap="xl" mih={160} style={{ pointerEvents: "none" }}>
          <Dropzone.Accept>
            <IconUpload size={40} />
          </Dropzone.Accept>
          <Dropzone.Reject>
            <IconX size={40} />
          </Dropzone.Reject>
          <Dropzone.Idle>
            <IconFileUpload size={40} />
          </Dropzone.Idle>
          <div>
            <Text size="lg">Arrasta faturas para aqui, ou clica para escolher</Text>
            <Text size="sm" c="dimmed">
              PDF, JPEG ou PNG, até 10 MB por ficheiro
            </Text>
          </div>
        </Group>
      </Dropzone>

      {items.length > 0 && (
        <Stack gap="xs">
          {items.map((item) => (
            <Paper key={item.id} withBorder p="sm" radius="md">
              <Group justify="space-between" mb={4}>
                <Text size="sm" truncate="end" maw={320}>
                  {item.file.name}
                </Text>
                {item.status === "done" && <IconCheck size={18} color="var(--mantine-color-green-6)" />}
                {item.status === "error" && <IconX size={18} color="var(--mantine-color-red-6)" />}
              </Group>
              {item.status === "error" ? (
                <Text size="xs" c="red">
                  {item.error}
                </Text>
              ) : (
                <Progress
                  value={item.progress}
                  color={item.status === "done" ? "green" : "indigo"}
                  animated={item.status === "uploading"}
                  aria-label={`Progresso de ${item.file.name}`}
                />
              )}
            </Paper>
          ))}
        </Stack>
      )}
    </Stack>
  );
}
