package no.ks.fiks.digisos.klient;

import no.ks.fiks.digisos.klient.model.DokumentInfo;
import no.ks.fiks.digisos.klient.model.FilOpplasting;
import no.ks.fiks.streaming.klient.KlientResponse;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.UUID;

public interface DigisosApi {

    KlientResponse<List<DokumentInfo>> lastOppFiler(List<FilOpplasting> dokumenter, UUID fiksOrgId, UUID digisosId);

    X509Certificate getDokumentlagerPublicKeyX509Certificate();

}
