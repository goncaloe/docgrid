import { Group, Select, TextInput } from "@mantine/core";

import type { DocumentStatus } from "../../api/types";

const STATUS_OPTIONS: { value: DocumentStatus; label: string }[] = [
  { value: "UPLOADED", label: "Enviado" },
  { value: "PROCESSING", label: "A processar" },
  { value: "EXTRACTED", label: "Extraído" },
  { value: "NEEDS_REVIEW", label: "Por rever" },
  { value: "FAILED", label: "Falhou" },
  { value: "APPROVED", label: "Aprovado" },
  { value: "REJECTED", label: "Rejeitado" },
  { value: "EXPORTED", label: "Exportado" },
];

export interface DocumentFiltersValue {
  status: DocumentStatus | null;
  supplierTaxId: string;
  /** Datas ISO `yyyy-mm-dd`, ou string vazia quando por preencher. */
  from: string;
  to: string;
}

export const EMPTY_DOCUMENT_FILTERS: DocumentFiltersValue = {
  status: null,
  supplierTaxId: "",
  from: "",
  to: "",
};

interface DocumentFiltersProps {
  value: DocumentFiltersValue;
  onChange: (value: DocumentFiltersValue) => void;
}

export function DocumentFilters({ value, onChange }: DocumentFiltersProps) {
  return (
    <Group align="flex-end" wrap="wrap">
      <Select
        label="Estado"
        placeholder="Todos"
        clearable
        data={STATUS_OPTIONS}
        value={value.status}
        onChange={(status) => onChange({ ...value, status: status as DocumentStatus | null })}
        w={180}
      />
      <TextInput
        label="NIF do fornecedor"
        placeholder="509000000"
        value={value.supplierTaxId}
        onChange={(event) => onChange({ ...value, supplierTaxId: event.currentTarget.value })}
        w={180}
      />
      <TextInput
        label="De"
        type="date"
        value={value.from}
        onChange={(event) => onChange({ ...value, from: event.currentTarget.value })}
        w={160}
      />
      <TextInput
        label="Até"
        type="date"
        value={value.to}
        onChange={(event) => onChange({ ...value, to: event.currentTarget.value })}
        w={160}
      />
    </Group>
  );
}
