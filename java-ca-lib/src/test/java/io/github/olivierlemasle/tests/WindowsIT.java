package io.github.olivierlemasle.tests;

import io.github.olivierlemasle.ca.CA;
import io.github.olivierlemasle.ca.CSR;
import io.github.olivierlemasle.ca.CertificateAuthority;
import io.github.olivierlemasle.ca.DistinguishedName;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.UUID;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class WindowsIT {

  @BeforeClass
  @AfterClass
  public static void clean() {
    final File caCertPath = new File("ca.cer");
    caCertPath.delete();
    final File reqPath = new File("cert.req");
    reqPath.delete();
    final File certPath = new File("cert.cer");
    certPath.delete();
  }

  @Test
  public void test() throws IOException, InterruptedException, CertificateEncodingException,
      NoSuchAlgorithmException {
    // Create a CA
    final DistinguishedName caName = CA.dn("CN=CA-Test");
    final CertificateAuthority ca = CA.init().setName(caName).build();
    // Export the CA certificate
    final X509Certificate caCert = ca.getCaCertificate();
    CA.export(caCert).saveCertificate("ca.cer");

    installTrustedCert("ca.cer");

    // Generate CSR using Windows utilities
    generateCsr();

    final CSR csr = CA.loadCsr("cert.req").getCsr();
    final X509Certificate cert = ca.sign(csr);
    CA.export(cert).saveCertificate("cert.cer");

    acceptCert("cert.cer");
    final String certThumbprint = getThumbPrint(cert);
    configureSsl(certThumbprint, UUID.randomUUID().toString());

    // NB: https binding has been set in appveyor.yml

    final URL url = new URL("https://localhost/");
    final URLConnection connection = url.openConnection();
    try (InputStream in = connection.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
      String line = null;
      while ((line = reader.readLine()) != null) {
        System.out.println(line);
      }
    }

  }

  private void installTrustedCert(final String certFileName) throws IOException,
      InterruptedException {
    final Process process = new ProcessBuilder("certutil", "-enterprise", "-addstore", "ROOT",
        certFileName)
        .redirectError(Redirect.INHERIT)
        .redirectOutput(Redirect.INHERIT)
        .start();

    process.waitFor();
  }

  private void generateCsr() throws IOException, InterruptedException {
    final Process process = new ProcessBuilder("certreq", "-new",
        "src\\test\\resources\\csr_template.inf", "cert.req")
        .redirectError(Redirect.INHERIT)
        .redirectOutput(Redirect.INHERIT)
        .start();

    process.waitFor();
  }

  private void acceptCert(final String certFileName) throws IOException,
      InterruptedException {
    final Process process = new ProcessBuilder("certeq", "-accept", certFileName)
        .redirectError(Redirect.INHERIT)
        .redirectOutput(Redirect.INHERIT)
        .start();

    process.waitFor();
  }

  private void configureSsl(final String certHash, final String appId) throws IOException,
      InterruptedException {
    final String certhashParam = "certhash=" + certHash;
    final String appidParam = "appid={" + appId + "}";
    final Process process = new ProcessBuilder("netsh", "http", "add", "sslcert",
        "ipport=0.0.0.0:443", "certstorename=MY", certhashParam, appidParam)
        .redirectError(Redirect.INHERIT)
        .redirectOutput(Redirect.INHERIT)
        .start();

    process.waitFor();
  }

  private static String getThumbPrint(final X509Certificate cert) throws NoSuchAlgorithmException,
      CertificateEncodingException {
    final MessageDigest md = MessageDigest.getInstance("SHA-1");
    final byte[] der = cert.getEncoded();
    md.update(der);
    final byte[] digest = md.digest();
    return hexify(digest);
  }

  private static String hexify(final byte bytes[]) {
    final char[] hexDigits = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c',
        'd', 'e', 'f' };

    final StringBuilder buf = new StringBuilder(bytes.length * 2);
    for (int i = 0; i < bytes.length; ++i) {
      buf.append(hexDigits[(bytes[i] & 0xf0) >> 4]);
      buf.append(hexDigits[bytes[i] & 0x0f]);
    }
    return buf.toString();
  }

}