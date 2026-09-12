import { useEffect, useRef, useState } from "react";
import { Alert, Button, Grid, Group, Kbd, Modal, Stack, Text, Title } from "@mantine/core";
import { useHotkeys } from "@mantine/hooks";
import { notifications } from "@mantine/notifications";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";

import { approveDocument, getDocument, rejectDocument } from "../../api/documents";
import type { ExtractedFieldName } from "../../api/types";
import { DocumentStatusBadge } from "../documents/DocumentStatusBadge";
import { needsManagerApproval } from "./approvalAuthority";
import { DocumentPreview } from "./DocumentPreview";
import { DuplicateCard } from "./DuplicateCard";
import { RejectModal } from "./RejectModal";
import { ReviewFieldsForm } from "./ReviewFieldsForm";
import { useReviewQueueNavigation } from "./useReviewQueueNavigation";
import { useAuth } from "../../auth/AuthContext";

const EDITABLE_STATUSES = new Set(["EXTRACTED", "NEEDS_REVIEW"]);

export function ReviewPage() {
  const { id } = useParams<{ id: string }>();
  const documentId = id ?? "";
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { session } = useAuth();

  const [activeField, setActiveField] = useState<ExtractedFieldName | null>(null);
  const [categoryDraft, setCategoryDraft] = useState("");
  const [rejectModalOpened, setRejectModalOpened] = useState(false);
  const [duplicateConfirmOpened, setDuplicateConfirmOpened] = useState(false);
  const initializedCategoryFor = useRef<string | null>(null);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["document", documentId],
    queryFn: () => getDocument(documentId),
    enabled: documentId !== "",
  });

  const navigation = useReviewQueueNavigation(documentId);

  useEffect(() => {
    if (data !== undefined && initializedCategoryFor.current !== data.id) {
      const categoryField = data.fields.find((field) => field.fieldName === "CATEGORY");
      setCategoryDraft(categoryField?.value ?? "");
      initializedCategoryFor.current = data.id;
    }
  }, [data]);

  const approveMutation = useMutation({
    mutationFn: () => approveDocument(documentId, categoryDraft.trim() === "" ? null : categoryDraft.trim()),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["review-queue"] });
      if (navigation.nextId !== null) {
        navigate(`/review/${navigation.nextId}`);
      } else {
        navigate("/review-queue");
        notifications.show({ title: "Documento aprovado", message: "Não há mais documentos à espera de revisão." });
      }
    },
    onError: (error) => {
      notifications.show({
        color: "red",
        title: "Não foi possível aprovar",
        message: error instanceof Error ? error.message : "Tenta novamente.",
      });
    },
  });

  const rejectMutation = useMutation({
    mutationFn: (reason: string) => rejectDocument(documentId, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["review-queue"] });
      void queryClient.invalidateQueries({ queryKey: ["document", documentId] });
      setRejectModalOpened(false);
      notifications.show({ title: "Documento rejeitado", message: "" });
    },
    onError: (error) => {
      notifications.show({
        color: "red",
        title: "Não foi possível rejeitar",
        message: error instanceof Error ? error.message : "Tenta novamente.",
      });
    },
  });

  const anyModalOpen = rejectModalOpened || duplicateConfirmOpened;

  /**
   * Pedir a aprovação. Um duplicado passa primeiro pelo aviso, e é preciso confirmá-lo
   * no próprio aviso (`confirmApprove`) — não basta o aviso estar aberto. Sem essa
   * distinção, dois `Ctrl+Enter` seguidos aprovavam um duplicado sem ninguém ler nada,
   * que é exatamente o reflexo de quem despacha uma fila à pressa.
   */
  function handleApprove() {
    if (data === undefined || !EDITABLE_STATUSES.has(data.status)) {
      return;
    }
    if (data.duplicateOfDocumentId !== null) {
      setDuplicateConfirmOpened(true);
      return;
    }
    approveMutation.mutate();
  }

  /** Só o botão do aviso de duplicado chega aqui: é esta a confirmação explícita. */
  function confirmApprove() {
    setDuplicateConfirmOpened(false);
    approveMutation.mutate();
  }

  // `@mantine/hooks` normaliza "Escape"/"Esc" para "esc" só do lado do evento, não do
  // combo pedido: tem de se escrever a combinação em minúsculas para bater certo.
  //
  // Com um modal aberto, os atalhos calam-se: o `Modal` do Mantine já trata o `Escape`
  // como "fechar", e sem esta guarda o mesmo evento fechava o modal e voltava a abri-lo
  // aqui — a rejeição ficava presa, sem saída pelo teclado.
  useHotkeys(
    anyModalOpen
      ? []
      : [
          ["mod+Enter", handleApprove],
          ["esc", () => setRejectModalOpened(true)],
        ],
    [],
  );

  if (isLoading) {
    return <Text c="dimmed">A carregar documento…</Text>;
  }

  if (isError || data === undefined) {
    return (
      <Alert color="red" title="Não foi possível carregar o documento">
        <Button size="xs" onClick={() => void refetch()}>
          Tentar de novo
        </Button>
      </Alert>
    );
  }

  const editable = EDITABLE_STATUSES.has(data.status);
  const showApprovalWarning = session !== null && needsManagerApproval(session.role, data.fields, data.validationResults);

  return (
    <Stack gap="md">
      <Group justify="space-between" wrap="wrap">
        <Group gap="sm">
          <Title order={2}>{data.originalFilename}</Title>
          <DocumentStatusBadge status={data.status} />
        </Group>
        {navigation.position !== null && navigation.total !== null && (
          <Text c="dimmed">
            {navigation.position} de {navigation.total}
          </Text>
        )}
        {editable && (
          <Group gap="xs">
            <Button onClick={handleApprove} loading={approveMutation.isPending}>
              Aprovar <Kbd ml={6}>Ctrl+Enter</Kbd>
            </Button>
            <Button
              variant="default"
              color="red"
              onClick={() => {
                setRejectModalOpened(true);
              }}
            >
              Rejeitar <Kbd ml={6}>Esc</Kbd>
            </Button>
          </Group>
        )}
      </Group>

      {showApprovalWarning && (
        <Alert color="yellow" title="Exige aprovação de gestor">
          Este documento excede o limite de aprovação ou tem um montante corrigido à mão: só um gestor ou
          administrador o pode aprovar.
        </Alert>
      )}

      {data.duplicateOfDocumentId !== null && (
        <DuplicateCard duplicateOfDocumentId={data.duplicateOfDocumentId} />
      )}

      <Grid>
        <Grid.Col span={{ base: 12, md: 6 }}>
          <DocumentPreview
            documentId={data.id}
            contentType={data.contentType}
            fields={data.fields}
            activeField={activeField}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, md: 6 }}>
          <ReviewFieldsForm
            documentId={data.id}
            fields={data.fields}
            validationResults={data.validationResults}
            editable={editable}
            onFocusField={setActiveField}
            onBlurField={() => {
              setActiveField(null);
            }}
            categoryDraft={categoryDraft}
            onCategoryDraftChange={setCategoryDraft}
          />
        </Grid.Col>
      </Grid>

      <RejectModal
        opened={rejectModalOpened}
        onClose={() => {
          setRejectModalOpened(false);
        }}
        onConfirm={(reason) => {
          rejectMutation.mutate(reason);
        }}
      />

      <Modal
        opened={duplicateConfirmOpened}
        onClose={() => {
          setDuplicateConfirmOpened(false);
        }}
        title="Aprovar um possível duplicado?"
      >
        <Text size="sm" mb="md">
          Este documento parece ser um duplicado de outro já aprovado. Tens a certeza de que queres aprová-lo na
          mesma?
        </Text>
        <Group justify="flex-end">
          <Button
            variant="default"
            onClick={() => {
              setDuplicateConfirmOpened(false);
            }}
          >
            Cancelar
          </Button>
          <Button color="orange" onClick={confirmApprove}>
            Aprovar mesmo assim
          </Button>
        </Group>
      </Modal>
    </Stack>
  );
}
