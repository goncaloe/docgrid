import { useState } from "react";
import { Button, Group, Modal, Textarea } from "@mantine/core";

interface RejectModalProps {
  opened: boolean;
  onClose: () => void;
  onConfirm: (reason: string) => void;
}

/** O motivo é obrigatório (`@NotBlank` no backend, `RejectRequest.reason`). */
export function RejectModal({ opened, onClose, onConfirm }: RejectModalProps) {
  const [reason, setReason] = useState("");

  function handleClose() {
    setReason("");
    onClose();
  }

  function handleConfirm() {
    const trimmed = reason.trim();
    if (trimmed === "") {
      return;
    }
    onConfirm(trimmed);
    setReason("");
  }

  return (
    <Modal opened={opened} onClose={handleClose} title="Rejeitar documento">
      <Textarea
        label="Motivo"
        placeholder="Não é uma fatura, é um postal publicitário"
        required
        autoFocus
        minRows={3}
        value={reason}
        onChange={(event) => {
          setReason(event.currentTarget.value);
        }}
        onKeyDown={(event) => {
          if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
            handleConfirm();
          }
        }}
      />
      <Group justify="flex-end" mt="md">
        <Button variant="default" onClick={handleClose}>
          Cancelar
        </Button>
        <Button color="red" onClick={handleConfirm} disabled={reason.trim() === ""}>
          Rejeitar
        </Button>
      </Group>
    </Modal>
  );
}
