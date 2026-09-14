import { Button, Group, Modal, Text } from "@mantine/core";

interface ConfirmCloseModalProps {
  opened: boolean;
  /** Nome do período que se vai fechar, para o título — p.ex. "agosto 2026". */
  periodLabel: string;
  onClose: () => void;
  onConfirm: () => void;
}

/**
 * O fecho de um período é irreversível e o modal tem de o dizer por palavras: mais adiante
 * não se pode reabrir nem alterar o CSV que o contabilista já recebeu.
 */
export function ConfirmCloseModal({ opened, periodLabel, onClose, onConfirm }: ConfirmCloseModalProps) {
  return (
    <Modal opened={opened} onClose={onClose} title={`Fechar ${periodLabel}`}>
      <Text size="sm">
        O CSV leva todos os documentos aprovados que ainda não foram exportados, com data de
        emissão anterior ao fim do mês. Depois de fechar, esses documentos ficam EXPORTED e
        já não se podem modificar nem entrar noutra exportação.
      </Text>
      <Group justify="flex-end" mt="md">
        <Button variant="default" onClick={onClose}>
          Cancelar
        </Button>
        <Button onClick={onConfirm}>Fechar período e exportar</Button>
      </Group>
    </Modal>
  );
}