package no.ks.fiks.digisos.klient.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NonNull;
import lombok.Value;

import java.util.UUID;

@Value
public class DokumentInfo {

  @JsonCreator
  public DokumentInfo(@JsonProperty("filnavn") @NonNull String filnavn, @JsonProperty("dokumentlagerDokumentId") @NonNull UUID dokumentlagerDokumentId, @JsonProperty("storrelse") @NonNull Long storrelse) {
    this.filnavn = filnavn;
    this.dokumentlagerDokumentId = dokumentlagerDokumentId;
    this.storrelse = storrelse;
  }

  @NonNull
  private String filnavn;

  @NonNull
  private UUID dokumentlagerDokumentId;

  @NonNull
  private Long storrelse;

}
