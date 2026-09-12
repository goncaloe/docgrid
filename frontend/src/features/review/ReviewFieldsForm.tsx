import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Stack, TextInput } from "@mantine/core";

import { correctField } from "../../api/documents";
import type { ExtractedFieldName, ExtractedFieldResponse, ValidationResultResponse } from "../../api/types";
import { ReviewField } from "./ReviewField";
import { FIELD_LABELS, FIELD_ORDER } from "./reviewFields";
import { messagesForField } from "./validationMessages";

interface ReviewFieldsFormProps {
  documentId: string;
  fields: ExtractedFieldResponse[];
  validationResults: ValidationResultResponse[];
  editable: boolean;
  onFocusField: (fieldName: ExtractedFieldName) => void;
  onBlurField: () => void;
  categoryDraft: string;
  onCategoryDraftChange: (value: string) => void;
}

/**
 * A categoria não passa por aqui: não há correção de campo para ela nesta etapa — vai no
 * corpo do `approve`, escolhida pelo revisor no momento de aprovar (`ApproveRequest.category`).
 */
const CORRECTABLE_FIELDS = FIELD_ORDER.filter((fieldName) => fieldName !== "CATEGORY");

export function ReviewFieldsForm({
  documentId,
  fields,
  validationResults,
  editable,
  onFocusField,
  onBlurField,
  categoryDraft,
  onCategoryDraftChange,
}: ReviewFieldsFormProps) {
  const queryClient = useQueryClient();

  const correction = useMutation({
    mutationFn: ({ fieldName, value }: { fieldName: ExtractedFieldName; value: string }) =>
      correctField(documentId, fieldName, value),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["document", documentId] });
      void queryClient.invalidateQueries({ queryKey: ["review-queue"] });
    },
  });

  function fieldValue(fieldName: ExtractedFieldName): ExtractedFieldResponse | undefined {
    return fields.find((field) => field.fieldName === fieldName);
  }

  return (
    <Stack gap="md">
      {CORRECTABLE_FIELDS.map((fieldName) => {
        const field = fieldValue(fieldName);
        return (
          <ReviewField
            key={fieldName}
            fieldName={fieldName}
            value={field?.value ?? ""}
            confidence={field?.confidence ?? null}
            source={field?.source ?? "AI"}
            messages={messagesForField(fieldName, validationResults)}
            editable={editable}
            onFocusField={onFocusField}
            onBlurField={onBlurField}
            onCorrect={(name, value) => {
              correction.mutate({ fieldName: name, value });
            }}
          />
        );
      })}
      <TextInput
        label={`${FIELD_LABELS.CATEGORY} (sugerida)`}
        value={categoryDraft}
        disabled={!editable}
        onChange={(event) => {
          onCategoryDraftChange(event.currentTarget.value);
        }}
      />
    </Stack>
  );
}
