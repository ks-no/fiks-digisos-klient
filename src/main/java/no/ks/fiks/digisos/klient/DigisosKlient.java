package no.ks.fiks.digisos.klient;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.NonNull;
import no.ks.fiks.digisos.klient.model.DokumentInfo;
import no.ks.fiks.digisos.klient.model.FilMetadata;
import no.ks.fiks.digisos.klient.model.FilOpplasting;
import no.ks.fiks.streaming.klient.*;
import no.ks.fiks.streaming.klient.authentication.AuthenticationStrategy;
import org.eclipse.jetty.client.util.MultiPartContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.MINUTES;

public class DigisosKlient {

    private String baseUrl;
    private StreamingKlient streamingKlient;

    public DigisosKlient(String baseUrl, @NonNull AuthenticationStrategy authenticationStrategy) {
        this.baseUrl = baseUrl;
        streamingKlient = new StreamingKlient(authenticationStrategy, 15L, MINUTES);
    }

    public KlientResponse<List<DokumentInfo>> lastOppFiler(@NonNull List<FilOpplasting> dokumenter, @NonNull UUID fiksOrgId, @NonNull UUID digisosId) {

        MultipartContentProviderBuilder multipartBuilder = new MultipartContentProviderBuilder();

        List<FilForOpplasting<Object>> filer = new ArrayList<>();

        dokumenter.forEach(dokument -> filer.add(FilForOpplasting.builder()
            .filnavn(dokument.getMetadata().getFilnavn())
            .metadata(FilMetadata.builder()
                .filnavn(dokument.getMetadata().getFilnavn())
                .mimetype(dokument.getMetadata().getMimetype())
                .storrelse(dokument.getMetadata().getStorrelse())
                .build())
            .data(dokument.getData())
            .build()));

        multipartBuilder.addFileData(filer);
        MultiPartContentProvider multiPartContentProvider = multipartBuilder.build();

        String requestId = UUID.randomUUID().toString();
        if (MDC.get("requestid") != null ) {
            requestId = MDC.get("requestid");
        }
        List<HttpHeader> httpHeaders = Collections.singletonList(HttpHeader.builder().headerName("requestid").headerValue(requestId).build());

        return streamingKlient.sendRequest(multiPartContentProvider, HttpMethod.POST, baseUrl, getLastOppFilerPath(fiksOrgId, digisosId), httpHeaders, new TypeReference<List<DokumentInfo>>() {});
    }

    private String getLastOppFilerPath(UUID fiksOrganisasjonId, UUID digisosId) {
        return String.format("/digisos/api/v1/%s/%s/filer", fiksOrganisasjonId, digisosId);
    }
}