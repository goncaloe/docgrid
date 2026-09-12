import { useEffect, useState } from "react";
import { Badge, Group, Text, TextInput, Tooltip } from "@mantine/core";

import type { ExtractedFieldName, FieldSource } from "../../api/types";
import { CONFIDENCE_THRESHOLD, FIELD_LABELS } from "./reviewFields";
import styles from "./ReviewField.module.css";

interface ReviewFieldProps {
  fieldName: ExtractedFieldName;
  value: string;
  confidence: number | null;
  source: FieldSource;
  messages: string[];
  editable: boolean;
  onFocusField: (fieldName: ExtractedFieldName) => void;
  onBlurField: () => void;
  onCorrect: (fieldName: ExtractedFieldName, value: string) => void;
}

/**
 * Um campo do formulário de revisão: rascunho local, gravado com um `PATCH` ao sair do
 * campo se o valor mudou. `Escape` repõe o valor do servidor sem gravar — o rascunho local
 * é o que torna isso possível sem um pedido extra.
 */
export function ReviewField({
  fieldName,
  value,
  confidence,
  source,
  messages,
  editable,
  onFocusField,
  onBlurField,
  onCorrect,
}: ReviewFieldProps) {
  const [draft, setDraft] = useState(value);
  const [isFocused, setIsFocused] = useState(false);

  useEffect(() => {
    if (!isFocused) {
      setDraft(value);
    }
  }, [value, isFocused]);

  const isUncertain = confidence !== null && confidence < CONFIDENCE_THRESHOLD;

  function handleBlur() {
    setIsFocused(false);
    onBlurField();
    if (draft !== value) {
      onCorrect(fieldName, draft);
    }
  }

  return (
    <TextInput
      label={
        <Group gap={6} wrap="nowrap">
          <span>{FIELD_LABELS[fieldName]}</span>
          {source === "HUMAN" ? (
            <Text span size="xs" c="dimmed">
              corrigido por si
            </Text>
          ) : (
            confidence !== null && (
              <Tooltip label="Grau de confiança da extração">
                <Badge color={isUncertain ? "yellow" : "gray"} variant="light" size="sm">
                  {String(Math.round(confidence * 100))}%
                </Badge>
              </Tooltip>
            )
          )}
        </Group>
      }
      value={draft}
      disabled={!editable}
      error={messages.length > 0 ? messages.join(" ") : undefined}
      className={isUncertain ? styles.fieldUncertain : undefined}
      onFocus={() => {
        setIsFocused(true);
        onFocusField(fieldName);
      }}
      onBlur={handleBlur}
      onChange={(event) => {
        setDraft(event.currentTarget.value);
      }}
      onKeyDown={(event) => {
        if (event.key === "Escape") {
          setDraft(value);
        }
      }}
    />
  );
}
