package no.ks.fiks.digisos.klient;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import no.ks.fiks.digisos.klient.model.DokumentInfo;
import no.ks.fiks.digisos.klient.model.FilOpplasting;
import no.ks.fiks.streaming.klient.KlientResponse;
import no.ks.kryptering.CMSKrypteringImpl;
import no.ks.kryptering.CMSStreamKryptering;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedOutputStream;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@Slf4j
public class DigisosKlient implements AutoCloseable {

    private final Provider provider = Security.getProvider("BC");
    private X509Certificate publicCertificate = null;
    private CMSStreamKryptering kryptering;
    private long timeoutSeconds;
    private DigisosApi digisosApi;
    private ExecutorService executor;

    private DigisosKlient(@NonNull DigisosApi digisosApi, @NonNull ExecutorService executor, @NonNull CMSStreamKryptering kryptering, long timeoutSeconds) {
        this.digisosApi = digisosApi;
        this.executor = executor;
        this.kryptering = kryptering;
        this.timeoutSeconds = timeoutSeconds;
    }

    public KlientResponse<List<DokumentInfo>> krypterOgLastOppFiler(@NonNull List<FilOpplasting> dokumenter, @NonNull UUID fiksOrgId, @NonNull UUID digisosId) {

        List<ListenableFuture<Boolean>> krypteringFutureList = Collections.synchronizedList(new ArrayList<>(dokumenter.size()));
        try {
            KlientResponse<List<DokumentInfo>> opplastetFiler = digisosApi.lastOppFiler(krypterFiler(dokumenter, krypteringFutureList), fiksOrgId, digisosId);
            log.debug("Sendt til Digisos API");

            waitForFutures(krypteringFutureList);
            return opplastetFiler;
        } finally {
            krypteringFutureList.stream().filter(f -> ! f.isDone() && ! f.isCancelled()).forEach(future -> future.cancel(true));
        }
    }

    @Override
    public void close(){
        executor.shutdownNow();
    }

    private void waitForFutures(List<ListenableFuture<Boolean>> krypteringFutureList) {
        ListenableFuture<Boolean> call = Futures.whenAllSucceed(krypteringFutureList).call(() -> true, executor);
        try {
            call.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        } catch (TimeoutException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<FilOpplasting> krypterFiler(@NonNull List<FilOpplasting> dokumenter, List<ListenableFuture<Boolean>> krypteringFutureList) {

        List<FilOpplasting> krypterteDokumenter = new ArrayList<>(dokumenter.size());

        dokumenter.forEach(dokument -> {
            krypterteDokumenter.add(new FilOpplasting(dokument.getMetadata(), krypter(dokument.getData(), krypteringFutureList)));
        });

        return krypterteDokumenter;
    }

    private InputStream krypter(@NonNull InputStream dokumentStream, List<ListenableFuture<Boolean>> krypteringFutureList) {

        if (publicCertificate == null) {
            publicCertificate = fetchDokumentlagerPublicCertificate();
        }

        DigisosPipedInputStream pipedInputStream = new DigisosPipedInputStream();
        try {

            PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream);
            ListenableFuture<Boolean> e1 = Futures.submitAsync(() -> {
                try {
                    log.debug("Starting encryption...");
                    kryptering.krypterData(pipedOutputStream, dokumentStream, publicCertificate, provider);
                    log.debug("Encryption completed");
                    return Futures.immediateFuture(true);
                } catch (Exception e) {
                    log.error("Encryption failed, setting exception on encrypted InputStream", e);
                    pipedInputStream.setException(e);
                    throw new IllegalStateException("An error occurred during encryption", e);
                } finally {
                    try {
                        log.debug("Closing encryption OutputStream");
                        pipedOutputStream.close();
                        log.debug("Encryption OutputStream closed");
                    } catch (IOException e) {
                        log.error("Failed closing encryption OutputStream", e);
                        throw new RuntimeException(e);
                    }
                }

            }, executor);
            krypteringFutureList.add(e1);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return pipedInputStream;
    }

    private X509Certificate fetchDokumentlagerPublicCertificate() {
        return digisosApi.getDokumentlagerPublicKeyX509Certificate();
    }

    public static DigisosKlientBuilder builder() {
        return new DigisosKlientBuilder();
    }

    public static class DigisosKlientBuilder {

        private DigisosApi digisosApi;
        private CMSStreamKryptering kryptering;
        private long timeoutSeconds = 60 * 5;
        private int antallThreads = 5;

        public DigisosKlientBuilder digisosApi(DigisosApi digisosApi) {
            this.digisosApi = digisosApi;
            return this;
        }

        public DigisosKlientBuilder kryptering(CMSStreamKryptering kryptering) {
            this.kryptering = kryptering;
            return this;
        }

        public DigisosKlientBuilder antallThreads(int antallThreads) {
            this.antallThreads = antallThreads;
            return this;
        }

        public DigisosKlientBuilder timeoutSeconds(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public DigisosKlient build() {
            if (antallThreads <= 0) {
                throw new IllegalArgumentException("Må ha minumum 1 tråd for kryptering");
            }
            if (timeoutSeconds <= 0) {
                throw new IllegalArgumentException("Må ha en timeout på minimum ett sekund");
            }
            if (kryptering == null) {
                kryptering = new CMSKrypteringImpl();
            }
            return new DigisosKlient(digisosApi, Executors.newFixedThreadPool(antallThreads), kryptering, timeoutSeconds);
        }

    }
}