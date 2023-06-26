package no.ks.fiks.digisos.klient.model;

import java.io.InputStream;

public record FilOpplasting(
        FilMetadata metadata,
        InputStream data
) {
}
