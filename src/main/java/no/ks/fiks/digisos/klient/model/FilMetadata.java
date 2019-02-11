package no.ks.fiks.digisos.klient.model;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
public class FilMetadata {

    @NonNull private String filnavn;
    @NonNull private String mimetype;
    @NonNull private Long storrelse;

}
