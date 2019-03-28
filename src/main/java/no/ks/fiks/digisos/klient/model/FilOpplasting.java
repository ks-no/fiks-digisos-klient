package no.ks.fiks.digisos.klient.model;

import lombok.Data;
import lombok.NonNull;

import java.io.InputStream;

@Data
public class FilOpplasting {

    @NonNull private FilMetadata metadata;
    @NonNull private InputStream data;

    FilOpplasting metadata(FilMetadata metadata) {
        this.metadata = metadata;
        return this;
    }

    FilOpplasting data(InputStream data) {
        this.data = data;
        return this;
    }

}
