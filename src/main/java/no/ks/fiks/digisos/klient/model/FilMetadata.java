package no.ks.fiks.digisos.klient.model;

public record FilMetadata(
        String filnavn,
        String mimetype,
        Long storrelse
) {
}
