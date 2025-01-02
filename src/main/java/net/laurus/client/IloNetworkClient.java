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
 * <p>
 * This class is responsible for:
 * <ul>
 *   <li>Performing HTTP requests to validate whether a given IPv4 address belongs to an iLO client.</li>
 *   <li>Parsing and validating XML responses.</li>
 *   <li>Blacklisting addresses that are unreachable or return invalid responses.</li>
 * </ul>
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
     * <p>
     * If the address is unreachable or returns an invalid response, it is added
     * to the blacklist to prevent future unnecessary requests.
     * 
     * @param ipAddress The {@link IPv4Address} to validate.
     * @return A {@link CompletableFuture} containing {@code true} if the address
     *         is a valid iLO client, or {@code false} otherwise.
     */
    public CompletableFuture<Boolean> isIloClientAsync(IPv4Address ipAddress) {
        if (networkCache.isBlacklisted(ipAddress)) {
            log.info("Skipping IP: {}", ipAddress.getAddress());
            return CompletableFuture.completedFuture(false);
        }
        else if (registrationHandler.isClientRegistered(ipAddress)) {
            log.info("Skipping IP: {}", ipAddress.getAddress());
            return CompletableFuture.completedFuture(true);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String endpoint = String.format(ILO_ENDPOINT_TEMPLATE, ipAddress.getAddress());
                HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(systemConfig.getIlo().getClientTimeoutConnect());
                connection.setReadTimeout(systemConfig.getIlo().getClientTimeoutRead());

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    log.info("Non-OK response for IP {}: {}", ipAddress.getAddress(), responseCode);
                    networkCache.addToBlacklist(ipAddress);
                    return false;
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    boolean isValid = isValidIloResponse(response.toString());
                    if (!isValid) {
                        log.info("Invalid response for IP {}. Adding to blacklist.", ipAddress.getAddress());
                        networkCache.addToBlacklist(ipAddress);
                    }
                    return isValid;
                }
            } catch (Exception e) {
                if (!e.getMessage().equalsIgnoreCase("Connect timed out")) {
                    log.error("Error validating IP {}: {}", ipAddress.getAddress(), e.getMessage());
                    networkCache.addToBlacklist(ipAddress);
                }
                return false;
            }
        });
    }

    /**
     * Validates the XML response to determine if it belongs to an iLO client.
     * <p>
     * This method checks if the response starts with the expected XML structure
     * and validates the root element.
     * 
     * @param xmlData The XML response as a string.
     * @return {@code true} if the response is valid and belongs to an iLO client,
     *         {@code false} otherwise.
     */
    private boolean isValidIloResponse(String xmlData) {
        if (xmlData == null || xmlData.isEmpty() || !xmlData.startsWith("<RIMP>")) {
            return false;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            Document document = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xmlData.getBytes()));
            return "RIMP".equals(document.getDocumentElement().getTagName());
        } catch (Exception e) {
            log.info("Invalid XML response: {}", e.getMessage());
            return false;
        }
    }
    
    public static void disableHostnameVerification() {
        try {
            // Trust all certificates
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

            // Ignore hostname verification
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

        } catch (Exception e) {
            throw new RuntimeException("Failed to disable SSL verification", e);
        }
    }
}
