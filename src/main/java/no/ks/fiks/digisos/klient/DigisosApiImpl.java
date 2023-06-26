package no.ks.fiks.digisos.klient;

import com.fasterxml.jackson.core.type.TypeReference;
import no.ks.fiks.digisos.klient.model.DokumentInfo;
import no.ks.fiks.digisos.klient.model.FilMetadata;
import no.ks.fiks.digisos.klient.model.FilOpplasting;
import no.ks.fiks.streaming.klient.*;
import org.eclipse.jetty.client.util.MultiPartRequestContent;
import org.eclipse.jetty.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

public class DigisosApiImpl implements DigisosApi {

    private static final Logger log = LoggerFactory.getLogger(DigisosApiImpl.class);

    private final StreamingKlient streamingKlient;
    private final String baseUrl;

    public DigisosApiImpl(StreamingKlient streamingKlient, String baseUrl) {
        this.streamingKlient = streamingKlient;
        this.baseUrl = baseUrl;
    }

    @Override
    public KlientResponse<List<DokumentInfo>> lastOppFiler(List<FilOpplasting> dokumenter, UUID fiksOrgId, UUID digisosId) {
        return doLastOppFiler(requireNonNull(dokumenter), requireNonNull(fiksOrgId), requireNonNull(digisosId));
    }

    public KlientResponse<List<DokumentInfo>> doLastOppFiler(List<FilOpplasting> dokumenter, UUID fiksOrgId, UUID digisosId) {

        MultipartContentProviderBuilder multipartBuilder = new MultipartContentProviderBuilder();

        List<FilForOpplasting<Object>> filer = new ArrayList<>();

        dokumenter.forEach(dokument -> filer.add(
                new FilForOpplasting<>(
                        dokument.metadata().filnavn(),
                        new FilMetadata(
                                dokument.metadata().filnavn(),
                                dokument.metadata().mimetype(),
                                dokument.metadata().storrelse()
                        ),
                        dokument.data()
                )
        ));

        multipartBuilder.addFileData(filer);
        MultiPartRequestContent multiPartContentProvider = multipartBuilder.build();

        List<HttpHeader> httpHeaders = Collections.singletonList(getHttpHeaderRequestId());

        log.debug("Starting upload...");
        KlientResponse<List<DokumentInfo>> response = streamingKlient.sendRequest(multiPartContentProvider, HttpMethod.POST, baseUrl, getLastOppFilerPath(fiksOrgId, digisosId), httpHeaders, new TypeReference<>() {});
        log.debug("Upload completed");

        return response;
    }

    @Override
    public X509Certificate getDokumentlagerPublicKeyX509Certificate() {

        List<HttpHeader> httpHeaders = Collections.singletonList(getHttpHeaderRequestId());
        KlientResponse<byte[]> response = streamingKlient.sendGetRawContentRequest(HttpMethod.GET, baseUrl, "/digisos/api/v1/dokumentlager-public-key", httpHeaders);

        byte[] publicKey = response.result();
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
        return new HttpHeader("requestid", requestId);
    }

}
