import { Group, TextInput } from "@mantine/core";

export interface PeriodFilterValue {
  /** Meses "yyyy-MM"; string vazia quando está por preencher. */
  from: string;
  to: string;
}

export const EMPTY_PERIOD_FILTER: PeriodFilterValue = { from: "", to: "" };

interface PeriodFilterProps {
  value: PeriodFilterValue;
  onChange: (value: PeriodFilterValue) => void;
}

export function PeriodFilter({ value, onChange }: PeriodFilterProps) {
  return (
    <Group align="flex-end" wrap="wrap">
      <TextInput
        label="De"
        type="month"
        value={value.from}
        onChange={(event) => onChange({ ...value, from: event.currentTarget.value })}
        w={160}
      />
      <TextInput
        label="Até"
        type="month"
        value={value.to}
        onChange={(event) => onChange({ ...value, to: event.currentTarget.value })}
        w={160}
      />
    </Group>
  );
}