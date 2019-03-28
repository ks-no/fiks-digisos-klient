package no.ks.fiks.digisos.klient;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import no.ks.fiks.digisos.klient.model.DokumentInfo;
import no.ks.fiks.digisos.klient.model.FilMetadata;
import no.ks.fiks.digisos.klient.model.FilOpplasting;
import no.ks.fiks.streaming.klient.*;
import org.eclipse.jetty.client.util.MultiPartContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.slf4j.MDC;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
public class DigisosApiImpl implements DigisosApi {

    private final StreamingKlient streamingKlient;
    private final String baseUrl;

    public DigisosApiImpl(StreamingKlient streamingKlient, String baseUrl) {
        this.streamingKlient = streamingKlient;
        this.baseUrl = baseUrl;
    }

    @Override
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

        List<HttpHeader> httpHeaders = Collections.singletonList(getHttpHeaderRequestId());

        log.debug("Starting upload...");
        KlientResponse<List<DokumentInfo>> response = streamingKlient.sendRequest(multiPartContentProvider, HttpMethod.POST, baseUrl, getLastOppFilerPath(fiksOrgId, digisosId), httpHeaders, new TypeReference<List<DokumentInfo>>() {});
        log.debug("Upload completed");

        return response;

    }

    @Override
    public X509Certificate getDokumentlagerPublicKeyX509Certificate() {

        List<HttpHeader> httpHeaders = Collections.singletonList(getHttpHeaderRequestId());
        KlientResponse<byte[]> response = streamingKlient.sendGetRawContentRequest(HttpMethod.GET, baseUrl, "/digisos/api/v1/dokumentlager-public-key", httpHeaders);

        byte[] publicKey = response.getResult();
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");

            return (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(publicKey));

        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }

    }

    private String getLastOppFilerPath(UUID fiksOrganisasjonId, UUID digisosId) {
        return String.format("/digisos/api/v1/%s/%s/filer", fiksOrganisasjonId, digisosId);
    }

    private HttpHeader getHttpHeaderRequestId() {
        String requestId = UUID.randomUUID().toString();
        if (MDC.get("requestid") != null) {
            requestId = MDC.get("requestid");
        }
        return HttpHeader.builder().headerName("requestid").headerValue(requestId).build();
    }

}
