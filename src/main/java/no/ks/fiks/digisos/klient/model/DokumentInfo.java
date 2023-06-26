package no.ks.fiks.digisos.klient.model;

import java.util.UUID;

public record DokumentInfo(
        String filnavn,
        UUID dokumentlagerDokumentId,
        Long storrelse
) {
}
