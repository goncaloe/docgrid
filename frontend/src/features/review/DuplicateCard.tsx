import { Alert, Anchor } from "@mantine/core";
import { Link } from "react-router-dom";

interface DuplicateCardProps {
  duplicateOfDocumentId: string;
}

/**
 * O aviso de duplicado, escrito aqui e não lido da mensagem da regra `DUPLICATE`.
 *
 * <p>Essa mensagem serve o log e a auditoria, não este ecrã: traz o id do documento em
 * bruto no meio da frase, e desaparece assim que uma correção manda revalidar — o
 * duplicado continua a existir (o detalhe calcula-o a cada leitura) mas a linha guardada
 * em `validation_results` já diz que a regra passou. O cartão ficava então com uma frase
 * de recurso, vaga, precisamente depois de quem revê ter mexido no documento.
 */
export function DuplicateCard({ duplicateOfDocumentId }: DuplicateCardProps) {
  return (
    <Alert color="orange" title="Possível duplicado">
      Este documento parece repetir outro que já está no sistema.{" "}
      <Anchor component={Link} to={`/review/${duplicateOfDocumentId}`}>
        Ver o documento original
      </Anchor>
    </Alert>
  );
}
