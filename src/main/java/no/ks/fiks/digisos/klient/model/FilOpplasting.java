package no.ks.fiks.digisos.klient.model;

import lombok.Data;
import lombok.NonNull;

import java.io.InputStream;

@Data
public class FilOpplasting {

    @NonNull private FilMetadata metadata;
    @NonNull private InputStream data;

}
