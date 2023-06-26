package no.ks.fiks.digisos.klient;

import no.ks.fiks.digisos.klient.model.DokumentInfo;
import no.ks.fiks.digisos.klient.model.FilMetadata;
import no.ks.fiks.digisos.klient.model.FilOpplasting;
import no.ks.fiks.streaming.klient.KlientResponse;
import no.ks.kryptering.CMSKrypteringImpl;
import no.ks.kryptering.CMSStreamKryptering;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Test DigisosKlient")
public class DigisosKlientTest {

    private static final String PUBLIC_KEY =
            """
                    -----BEGIN CERTIFICATE-----
                    MIIEIDCCAwigAwIBAgIJAOfdsbcJ9VCaMA0GCSqGSIb3DQEBCwUAMIGjMQswCQYD
                    VQQGEwJOTzEWMBQGA1UECAwNRG9rdW1lbnRsYWdlcjEWMBQGA1UEBwwNRG9rdW1l
                    bnRsYWdlcjEWMBQGA1UECgwNRG9rdW1lbnRsYWdlcjEWMBQGA1UECwwNRG9rdW1l
                    bnRsYWdlcjEWMBQGA1UEAwwNRG9rdW1lbnRsYWdlcjEcMBoGCSqGSIb3DQEJARYN
                    RG9rdW1lbnRsYWdlcjAgFw0xOTAxMjIwNzExNDlaGA80NzU2MTIxODA3MTE0OVow
                    gaMxCzAJBgNVBAYTAk5PMRYwFAYDVQQIDA1Eb2t1bWVudGxhZ2VyMRYwFAYDVQQH
                    DA1Eb2t1bWVudGxhZ2VyMRYwFAYDVQQKDA1Eb2t1bWVudGxhZ2VyMRYwFAYDVQQL
                    DA1Eb2t1bWVudGxhZ2VyMRYwFAYDVQQDDA1Eb2t1bWVudGxhZ2VyMRwwGgYJKoZI
                    hvcNAQkBFg1Eb2t1bWVudGxhZ2VyMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIB
                    CgKCAQEAozKOfBKRoU7AgRAfvROwAKGPuZOyp5x7WB0pZZca7xhB01k0CZGsPr42
                    6B1MtufuDlbMEBnUsbuPGrU5jZ02OOcITXa9t8t4GF0UnwffYa9Jn1GewTYzP0oo
                    rNCXMJyzsZOVUSOvctG/X5z8i5TZs9gtSYun0rvBqENKGJZubx67aTtABfAuDioY
                    xsW0KBt2LuhrcykoH9hJYdPBvS8PuCAIzhXxWG/VEHAnS+x4jpR7UkKt3yGtRa8s
                    OZ94xosXjNj6vAtb1TpvcfZV/9E2bxJtUVIPaAS2jt2Qo0pc6ea05MSSxsl574am
                    J/F9nQ9FMs6t9ZIeBdU95abu8rOxOwIDAQABo1MwUTAdBgNVHQ4EFgQUHnsFG7Kx
                    IOwibkHTnBwY79jbjKkwHwYDVR0jBBgwFoAUHnsFG7KxIOwibkHTnBwY79jbjKkw
                    DwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAGBJOnx1zOZYbwqxG
                    iGbR98ms2OydjUBbaiB9SneWomTXGXSI3j/7xUlCyQFLQiivUI2Ip5x0nhPMOaYK
                    yTYy6rZ/geBmcOpWihd4LnNLO3AT2dYcptdG213yIomjRk4BNaAQCMt9ZcicTzxG
                    eV1oa2ERdRt+y8fkVd0QZ6lhXOssW2vMt9AC2k4LL9woJrgZs4CvtCDKHET+HvvI
                    7NbuaTZSNolZwR5hIdtq8nKCvNVp4VvOFgT8WIuudMx1tWgDIo9ttLCV7tz9WjtL
                    L9fbdxYO5UGayVq0IFt4gCQLpkcaThaqRVeC+l7PU7WHHqrUzsnSQpm8Hp40D2zI
                    dkMkSA==
                    -----END CERTIFICATE-----""";
    private static final String PRIVATE_KEY =
            """
                    MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCjMo58EpGhTsCB
                    EB+9E7AAoY+5k7KnnHtYHSlllxrvGEHTWTQJkaw+vjboHUy25+4OVswQGdSxu48a
                    tTmNnTY45whNdr23y3gYXRSfB99hr0mfUZ7BNjM/Siis0JcwnLOxk5VRI69y0b9f
                    nPyLlNmz2C1Ji6fSu8GoQ0oYlm5vHrtpO0AF8C4OKhjGxbQoG3Yu6GtzKSgf2Elh
                    08G9Lw+4IAjOFfFYb9UQcCdL7HiOlHtSQq3fIa1Fryw5n3jGixeM2Pq8C1vVOm9x
                    9lX/0TZvEm1RUg9oBLaO3ZCjSlzp5rTkxJLGyXnvhqYn8X2dD0Uyzq31kh4F1T3l
                    pu7ys7E7AgMBAAECggEALKBJiDIHsqV3TJOdKjX0/ecwBx4VT3Ih5HFs/YO5cMIg
                    Vevhp/A2up2HJCfG74kydqdTe9+kYsmYE0SVLV1dE2hRw+UBcf3opDjnx6j+c5bc
                    Of22vLzWfKsJvl/3x+pB1QA3Z42rj2k9vKaQBJc6hMxLbf4LcTu4dAuaemjAYBAG
                    gXoBeG5m7APYEaGIbGdl8UEWMf/GrarDMAYMWoatKIRkhwYVazfjaoVxk2kybCA0
                    RjsGjrXTETojKmFi2ImQYC9VVdOSGBlqTfuJHv3MaWOD09W3/IfRrMWag0UrX1zB
                    T4IXnZFl+1AHVJ6AXC6114mW1hFJuxGnAlXbEHUJOQKBgQDPN0gvU5l/MD1UzL8Y
                    kyiJtE6fjXo2Z0mcwXv/ZocKq/BvuEjbav4QzmF/6oiC9zpgmbvxsZpjyz8vkqc6
                    vDTmE07Bkp+bxXXI821KONLCsIyRxDXT7JSyRWCoD0TAEX/IkYh6GWAxqtnzaTQi
                    gqelHI/oId+fIp6K9UHBGAW7JQKBgQDJnlJjpJWEcvLOjhST3m7eeGotrR2SO/U9
                    +nouLnGglDpEp8UbvLbTPLAoYBRaFLxr95quCh/+96f2wmrcfWGt+K4K3LKtDZbu
                    0Clg4RuhZWokTJ7QNpKgTGmQCsboMM9AJTBPQCj2uESb7+V/LVTPzyvs9U7RgaRn
                    8YLqI6U83wKBgBWbnCljPFRpAVxAZYT4g3eol7JHnIDj0GdKPdXqKRbRyya7Ps2y
                    oH+8JaqjGE0f3rSIE3MmpATYAuTBFDMpwRJk3QeOdJpXwuqLh8//kOrAYkgo/7vz
                    paXZWjTsMq0cpgiSNHsW/lLvj/6z773Rhg3Ppqn8Lkd34rR20r6B9McJAoGAH+JF
                    rTRN4NA8zaVyY5/9cHkicW67CnEo61A9GiiGF5rZTBor9aL2Vpl2Uiw/i69TzM8v
                    Su6W+L85dLByLcQ2OkjlXRphtzQ69jE9GfD/aZqcGnlzdAHtViQ/XWQW6IkvfTlk
                    VmQTFlE1qGNbq60DiIl+rM5uVHtoAHgU9+oDK4kCgYBwAD1SPW7b3OKoleyjUew6
                    FoJ952ShhEeYmyMpwhYayUY1SEdcjJlxDj5RyVCvWq2xu7LL/7uLRaQR9XiLLwxE
                    m+q8ASYbc1Fh8UykUNFcOKqTImWA0CGuDndc1ZXxwA9SN9/UNap21dfZrJN/VhBN
                    Ad1DLPxU/e3rN6lr/Yopqw==
                    """;
    private final CMSKrypteringImpl kryptering = new CMSKrypteringImpl();
    private final Provider provider = Security.getProvider("BC");
    private final PrivateKey privateKey = getPrivateKey();
    @Mock
    private DigisosApi digisosApi;

    private PrivateKey getPrivateKey() {
        try {
            Base64.Decoder base64 = Base64.getMimeDecoder();
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(base64.decode(PRIVATE_KEY));
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    private X509Certificate getX509Certificate() {
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            Base64.Decoder base64 = Base64.getMimeDecoder();

            byte[] buffer = base64.decode(
                    PUBLIC_KEY.replace("-----BEGIN CERTIFICATE-----", "")
                            .replace("-----END CERTIFICATE-----", ""));

            return (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(buffer));

        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    private void assertDataEncrypted(byte[] originalData, byte[] encryptedData) {
        System.out.println("TEST: Starting decryption...");
        byte[] decryptedData = kryptering.dekrypterData(encryptedData, privateKey, provider);
        assertArrayEquals(originalData, decryptedData);
        System.out.println("TEST: Decryption completed.");
    }

    @Test
    @DisplayName("Test at ett enkelt dokument blir kryptert")
    void krypteringOgOpplastingAvEttDokument() {

        when(digisosApi.getDokumentlagerPublicKeyX509Certificate()).thenReturn(getX509Certificate());
        int size = 1024 * 1024 * 10;
        byte[] data = new byte[size];
        new Random().nextBytes(data);

        FilMetadata metadata = new FilMetadata(
                "small.pdf",
                "application/pdf",
                Integer.toUnsignedLong(data.length)
        );

        FilOpplasting filOpplasting = new FilOpplasting(metadata, new ByteArrayInputStream(data));

        when(digisosApi.lastOppFiler(anyList(), isA(UUID.class), isA(UUID.class))).then(assertingAnswerForDigisosApi(Collections.singletonList(data)));

        try (DigisosKlient digisosKlient = DigisosKlient.builder().digisosApi(digisosApi).timeoutSeconds(1).build()) {
            KlientResponse<List<DokumentInfo>> response = digisosKlient.krypterOgLastOppFiler(singletonList(filOpplasting), UUID.randomUUID(), UUID.randomUUID());
            assertEquals(1, response.result().size());
        }
        verify(digisosApi).lastOppFiler(anyList(), isA(UUID.class), isA(UUID.class));

    }

    @Test
    @DisplayName("Test at to dokumenter blir kryptert")
    void krypteringOgOpplastingAvToDokumenter() {

        when(digisosApi.getDokumentlagerPublicKeyX509Certificate()).thenReturn(getX509Certificate());

        int size = 1024 * 1024 * 10;
        byte[] data = new byte[size];
        new Random().nextBytes(data);

        FilMetadata metadata = new FilMetadata(
                "small.pdf",
                "application/pdf",
                Integer.toUnsignedLong(data.length)
        );

        FilOpplasting filOpplasting = new FilOpplasting(metadata, new ByteArrayInputStream(data));

        byte[] data2 = new byte[1024 * 1024 * 10];
        new Random().nextBytes(data2);
        FilOpplasting filOpplasting2 = new FilOpplasting(metadata, new ByteArrayInputStream(data2));

        when(digisosApi.lastOppFiler(anyList(), isA(UUID.class), isA(UUID.class))).then(assertingAnswerForDigisosApi(asList(data, data2)));

        try (DigisosKlient digisosKlient = DigisosKlient.builder().digisosApi(digisosApi).build()) {
            digisosKlient.krypterOgLastOppFiler(asList(filOpplasting, filOpplasting2), UUID.randomUUID(), UUID.randomUUID());
        }
        verify(digisosApi).lastOppFiler(anyList(), isA(UUID.class), isA(UUID.class));
    }

    @Test
    @DisplayName("Test at mange dokumenter blir kryptert")
    void krypteringOgOpplastingAvMangeDokumenter() {

        when(digisosApi.getDokumentlagerPublicKeyX509Certificate()).thenReturn(getX509Certificate());

        FilMetadata metadata = new FilMetadata(
                "small.pdf",
                "application/pdf",
                123L
        );

        int numDokumenter = 100;
        List<FilOpplasting> flereDokumenter = new ArrayList<>(numDokumenter);
        List<byte[]> orginaleDokumenterBytes = new ArrayList<>(numDokumenter);
        for (int i = 0; i < numDokumenter; i++) {
            byte[] data = new byte[new Random().nextInt(1024 * 1024 * 10)];
            new Random().nextBytes(data);
            orginaleDokumenterBytes.add(data);
            flereDokumenter.add(new FilOpplasting(metadata, new ByteArrayInputStream(data)));
        }

        when(digisosApi.lastOppFiler(anyList(), isA(UUID.class), isA(UUID.class))).then(assertingAnswerForDigisosApi(orginaleDokumenterBytes));

        try (DigisosKlient digisosKlient = DigisosKlient.builder().digisosApi(digisosApi).build()) {
            digisosKlient.krypterOgLastOppFiler(flereDokumenter, UUID.randomUUID(), UUID.randomUUID());
        }
        verify(digisosApi).lastOppFiler(anyList(), isA(UUID.class), isA(UUID.class));
    }

    @Test
    @DisplayName("Dersom kryptering feiler skal det kastes exception")
    void krypteringFeilerOgKasterException() {

        when(digisosApi.getDokumentlagerPublicKeyX509Certificate()).thenReturn(getX509Certificate());
        int size = 1024 * 1024 * 10;
        byte[] data = new byte[size];
        new Random().nextBytes(data);

        FilMetadata metadata = new FilMetadata(
                "small.pdf",
                "application/pdf",
                Integer.toUnsignedLong(data.length)
        );

        FilOpplasting filOpplasting = new FilOpplasting(metadata, new ByteArrayInputStream(data));

        Exception expected = new RuntimeException("Kryptering feilet");
        CMSStreamKryptering krypteringMock = mock(CMSStreamKryptering.class);
        doThrow(expected).when(krypteringMock).krypterData(any(), any(), any(), any());

        try (DigisosKlient digisosKlient = DigisosKlient.builder().digisosApi(digisosApi).kryptering(krypteringMock).build()) {

            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> digisosKlient.krypterOgLastOppFiler(singletonList(filOpplasting), UUID.randomUUID(), UUID.randomUUID()));
            assertEquals("Kryptering feilet", exception.getCause().getCause().getCause().getMessage());
        }
        verify(digisosApi).lastOppFiler(anyList(), isA(UUID.class), isA(UUID.class));
    }

    @Test
    @DisplayName("Dersom kryptering feiler på andre fil, skal det kastes exception")
    void krypteringFeilerPaaFil2OgKasterException() {

        when(digisosApi.getDokumentlagerPublicKeyX509Certificate()).thenReturn(getX509Certificate());

        int size = 1024 * 1024 * 10;
        byte[] data = new byte[size];
        new Random().nextBytes(data);

        FilMetadata metadata = new FilMetadata(
                "small.pdf",
                "application/pdf",
                Integer.toUnsignedLong(data.length)
        );

        FilOpplasting filOpplasting = new FilOpplasting(metadata, new ByteArrayInputStream(data));

        byte[] data2 = new byte[416];
        new Random().nextBytes(data2);
        FilOpplasting filOpplasting2 = new FilOpplasting(metadata, new ByteArrayInputStream(data2));

        Exception expected = new RuntimeException("Kryptering feilet");
        CMSStreamKryptering krypteringMock = mock(CMSStreamKryptering.class);
        doThrow(expected).when(krypteringMock).krypterData(any(), any(), any(), any());

        try (DigisosKlient digisosKlient = DigisosKlient.builder().digisosApi(digisosApi).kryptering(krypteringMock).build()) {
            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> digisosKlient.krypterOgLastOppFiler(asList(filOpplasting, filOpplasting2), UUID.randomUUID(), UUID.randomUUID()));
            assertEquals("Kryptering feilet", exception.getCause().getCause().getCause().getMessage());
        }
    }

    @Test
    @DisplayName("Test opplasting av mange dokumenter til forskjellige saker")
    void opplastingAvMangeDokumenterTilUlikeSaker() {
        when(digisosApi.getDokumentlagerPublicKeyX509Certificate()).thenReturn(getX509Certificate());

        FilMetadata metadata = new FilMetadata(
                "small.pdf",
                "application/pdf",
                123L
        );

        int numDokumenter = 100;
        List<FilOpplasting> flereDokumenter1 = new ArrayList<>(numDokumenter);
        List<FilOpplasting> flereDokumenter2 = new ArrayList<>(numDokumenter);
        List<byte[]> orginaleDokumenterBytes1 = new ArrayList<>(numDokumenter);
        List<byte[]> orginaleDokumenterBytes2 = new ArrayList<>(numDokumenter);

        for (int i = 0; i < numDokumenter; i++) {
            byte[] data = new byte[1024 * 1024 * 10];
            new Random().nextBytes(data);
            orginaleDokumenterBytes1.add(data);
            flereDokumenter1.add(new FilOpplasting(metadata, new ByteArrayInputStream(data)));

            data = new byte[1024 * 1024];
            new Random().nextBytes(data);
            orginaleDokumenterBytes2.add(data);
            flereDokumenter2.add(new FilOpplasting(metadata, new ByteArrayInputStream(data)));
        }

        try (DigisosKlient digisosKlient = DigisosKlient.builder().digisosApi(digisosApi).build()) {

            when(digisosApi.lastOppFiler(anyList(), isA(UUID.class), isA(UUID.class))).then(assertingAnswerForDigisosApi(orginaleDokumenterBytes1));
            digisosKlient.krypterOgLastOppFiler(flereDokumenter1, UUID.randomUUID(), UUID.randomUUID());

            when(digisosApi.lastOppFiler(anyList(), isA(UUID.class), isA(UUID.class))).then(assertingAnswerForDigisosApi(orginaleDokumenterBytes2));
            digisosKlient.krypterOgLastOppFiler(flereDokumenter2, UUID.randomUUID(), UUID.randomUUID());

        }
        verify(digisosApi, times(2)).lastOppFiler(anyList(), isA(UUID.class), isA(UUID.class));
    }

    @Test
    @DisplayName("Test at kryptering gir timeout når ingen leser fra streamen")
    void krypteringGirTimeout() {

        when(digisosApi.getDokumentlagerPublicKeyX509Certificate()).thenReturn(getX509Certificate());

        FilMetadata metadata = new FilMetadata(
                "small.pdf",
                "application/pdf",
                123L
        );

        int numDokumenter = 100;
        List<FilOpplasting> flereDokumenter = new ArrayList<>(numDokumenter);
        for (int i = 0; i < numDokumenter; i++) {
            byte[] data = new byte[new Random().nextInt(1024 * 1024 * 10)];
            new Random().nextBytes(data);
            flereDokumenter.add(new FilOpplasting(metadata, new ByteArrayInputStream(data)));
        }

        DigisosKlient digisosKlient = DigisosKlient.builder().digisosApi(digisosApi).timeoutSeconds(3).build();
        IllegalStateException timeoutException = assertThrows(IllegalStateException.class, () -> digisosKlient.krypterOgLastOppFiler(flereDokumenter, UUID.randomUUID(), UUID.randomUUID()));
        assertEquals(TimeoutException.class, timeoutException.getCause().getClass());
    }

    @Test
    @DisplayName("Test opprettelse av DigisosKlientBuilder med feile verdier kaster exception")
    void testFeilInputTilBuilder() {
        IllegalArgumentException illegalTimeoutException = assertThrows(IllegalArgumentException.class, () -> DigisosKlient.builder()
                .digisosApi(digisosApi)
                .timeoutSeconds(0)
                .build());
        assertEquals("Må ha en timeout på minimum ett sekund", illegalTimeoutException.getMessage());
        illegalTimeoutException = assertThrows(IllegalArgumentException.class, () -> DigisosKlient.builder()
                .digisosApi(digisosApi)
                .timeoutSeconds(-10)
                .build());
        assertEquals("Må ha en timeout på minimum ett sekund", illegalTimeoutException.getMessage());

        IllegalArgumentException illegalNumThreadsException = assertThrows(IllegalArgumentException.class, () -> DigisosKlient.builder()
                .digisosApi(digisosApi)
                .antallThreads(0)
                .build());
        assertEquals("Må ha minumum 1 tråd for kryptering", illegalNumThreadsException.getMessage());
        illegalNumThreadsException = assertThrows(IllegalArgumentException.class, () -> DigisosKlient.builder()
                .digisosApi(digisosApi)
                .antallThreads(-10)
                .build());
        assertEquals("Må ha minumum 1 tråd for kryptering", illegalNumThreadsException.getMessage());

        verifyNoInteractions(digisosApi);
    }

    private Answer<KlientResponse<List<DokumentInfo>>> assertingAnswerForDigisosApi(final List<byte[]> ukrypterteFiler) {
        return a -> {
            final List<FilOpplasting> filer = a.getArgument(0);
            assertEquals(filer.size(), ukrypterteFiler.size());
            for (int i = 0; i < filer.size(); i++) {
                assertDataEncrypted(ukrypterteFiler.get(i), IOUtils.toByteArray(filer.get(i).data()));
            }
            return new KlientResponse<>(
                    filer.stream()
                            .map(f -> new DokumentInfo(f.metadata().filnavn(), UUID.randomUUID(), f.metadata().storrelse()))
                            .collect(Collectors.toList()),
                    HttpStatus.OK_200,
                    null
            );
        };
    }

}
