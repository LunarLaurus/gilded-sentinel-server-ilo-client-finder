package net.laurus.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.laurus.component.NetworkCache;
import net.laurus.component.RegistrationCache;
import net.laurus.config.SystemConfig;
import net.laurus.network.IPv4Address;

/**
 * Handles network interactions for iLO client validation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IloNetworkClient {

    private static final String ILO_ENDPOINT_TEMPLATE = "https://%s/xmldata?item=all";

    static {
        disableHostnameVerification();
    }

    private final SystemConfig systemConfig;
    private final NetworkCache networkCache;
    private final RegistrationCache registrationHandler;

    /**
     * Asynchronously checks if the given IPv4 address belongs to an iLO client.
     *
     * @param ipAddress The {@link IPv4Address} to validate.
     * @return A {@link CompletableFuture} with {@code true} if valid; {@code false} otherwise.
     */
    public CompletableFuture<Boolean> isIloClient(IPv4Address ipAddress) {
        if (networkCache.isBlacklisted(ipAddress)) {
            log.info("Skipping blacklisted IP: {}", ipAddress.toString());
            return CompletableFuture.completedFuture(false);
        }
        if (registrationHandler.isClientRegistered(ipAddress)) {
            log.info("Skipping registered IP: {}", ipAddress.toString());
            return CompletableFuture.completedFuture(true);
        }

        return CompletableFuture.supplyAsync(() -> validateIloClient(ipAddress));
    }

    private boolean validateIloClient(IPv4Address ipAddress) {
        String endpoint = String.format(ILO_ENDPOINT_TEMPLATE, ipAddress.toString());
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(systemConfig.getIlo().getClientTimeoutConnect());
            connection.setReadTimeout(systemConfig.getIlo().getClientTimeoutRead());

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                log.info("Non-OK response for IP {}: {}", ipAddress.toString(), connection.getResponseCode());
                networkCache.addToBlacklist(ipAddress);
                return false;
            }

            String response = new BufferedReader(new InputStreamReader(connection.getInputStream()))
                .lines()
                .reduce("", String::concat);

            boolean isValid = isValidIloResponse(response);
            if (!isValid) {
                log.info("Invalid response for IP {}. Blacklisting.", ipAddress.toString());
                networkCache.addToBlacklist(ipAddress);
            }
            return isValid;

        } catch (Exception e) {
            handleNetworkError(ipAddress, e);
            return false;
        }
    }

    private void handleNetworkError(IPv4Address ipAddress, Exception e) {
        if (!"Connect timed out".equalsIgnoreCase(e.getMessage())) {
            log.error("Error validating IP {}: {}", ipAddress.toString(), e.getMessage());
        }
        networkCache.addToBlacklist(ipAddress);
    }

    /**
     * Validates the XML response for iLO compliance.
     *
     * @param xmlData The XML response as a string.
     * @return {@code true} if valid; {@code false} otherwise.
     */
    private boolean isValidIloResponse(String xmlData) {
        if (xmlData == null || !xmlData.startsWith("<RIMP>")) {
            return false;
        }

        try {
            Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(xmlData.getBytes()));

            return "RIMP".equals(document.getDocumentElement().getTagName());
        } catch (Exception e) {
            log.info("Invalid XML response: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Disables hostname verification and trusts all SSL certificates.
     */
    private static void disableHostnameVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}

                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to disable SSL verification", e);
        }
    }
}
