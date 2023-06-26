package no.ks.fiks.digisos.klient;

import no.ks.fiks.digisos.klient.model.DokumentInfo;
import no.ks.fiks.digisos.klient.model.FilOpplasting;
import no.ks.fiks.streaming.klient.KlientResponse;
import no.ks.kryptering.CMSKrypteringImpl;
import no.ks.kryptering.CMSStreamKryptering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class DigisosKlient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DigisosKlient.class);

    private final Provider provider = Security.getProvider("BC");
    private X509Certificate publicCertificate = null;
    private final CMSStreamKryptering kryptering;
    private final long timeoutSeconds;
    private final DigisosApi digisosApi;
    private final ExecutorService executor;

    private DigisosKlient(DigisosApi digisosApi, ExecutorService executor, CMSStreamKryptering kryptering, long timeoutSeconds) {
        this.digisosApi = requireNonNull(digisosApi);
        this.executor = requireNonNull(executor);
        this.kryptering = requireNonNull(kryptering);
        this.timeoutSeconds = timeoutSeconds;
    }

    public KlientResponse<List<DokumentInfo>> krypterOgLastOppFiler(List<FilOpplasting> dokumenter, UUID fiksOrgId, UUID digisosId) {
        return doKrypterOgLastOppFiler(requireNonNull(dokumenter), requireNonNull(fiksOrgId), requireNonNull(digisosId));
    }

    private KlientResponse<List<DokumentInfo>> doKrypterOgLastOppFiler(List<FilOpplasting> dokumenter, UUID fiksOrgId, UUID digisosId) {
        final List<CompletableFuture<Void>> krypteringFutureList = Collections.synchronizedList(new ArrayList<>(dokumenter.size()));
        try {
            KlientResponse<List<DokumentInfo>> opplastetFiler = digisosApi.lastOppFiler(dokumenter.stream()
                    .map(dokument -> new FilOpplasting(dokument.metadata(), krypter(dokument.data(), krypteringFutureList)))
                    .collect(Collectors.toList()), fiksOrgId, digisosId);


            waitForFutures(krypteringFutureList);
            log.info("{} dokumenter lagt til digisosId {} på fiksOrg {}", dokumenter.size(), digisosId, fiksOrgId);
            return opplastetFiler;
        } finally {
            krypteringFutureList.stream().filter(f -> !f.isDone() && !f.isCancelled()).forEach(future -> future.cancel(true));
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private void waitForFutures(List<CompletableFuture<Void>> krypteringFutureList) {
        final CompletableFuture<Void> allFutures = CompletableFuture.allOf(krypteringFutureList.toArray(new CompletableFuture[]{}));
        try {
            allFutures.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (CompletionException e) {
            throw new IllegalStateException(e.getCause());
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private InputStream krypter(InputStream dokumentStream, List<CompletableFuture<Void>> krypteringFutureList) {
        return doKrypter(requireNonNull(dokumentStream), krypteringFutureList);
    }

    private InputStream doKrypter(InputStream dokumentStream, List<CompletableFuture<Void>> krypteringFutureList) {

        if (publicCertificate == null) {
            publicCertificate = fetchDokumentlagerPublicCertificate();
        }

        PipedInputStream pipedInputStream = new PipedInputStream();
        try {
            PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream);
            CompletableFuture<Void> krypteringFuture = CompletableFuture.runAsync(() -> {
                try {
                    log.debug("Starting encryption...");
                    kryptering.krypterData(pipedOutputStream, dokumentStream, publicCertificate, provider);
                    log.debug("Encryption completed");
                } catch (Exception e) {
                    log.error("Encryption failed, setting exception on encrypted InputStream", e);
                    throw new IllegalStateException("An error occurred during encryption", e);
                } finally {
                    try {
                        log.debug("Closing encryption OutputStream");
                        pipedOutputStream.close();
                        log.debug("Encryption OutputStream closed");
                    } catch (IOException e) {
                        log.error("Failed closing encryption OutputStream", e);
                    }
                }

            }, executor);
            krypteringFutureList.add(krypteringFuture);

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