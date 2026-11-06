package dev.badr.pocketdrop;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Optional check (needs administrator rights): makes api.telegram.org point to THIS computer for a few
 * seconds, with a certificate for that name, and runs the bot tests WITHOUT any test-only address. This
 * proves the real addresses (including the library's own file download) work with host name checking on.
 * It restores the hosts file afterwards. It is skipped when it is not allowed to edit the hosts file.
 */
public final class ProductionPathCheck {
    public static void main(String[] args) throws Exception {
        Path hosts = Path.of(System.getProperty("os.name").toLowerCase().contains("win")
                ? System.getenv("SystemRoot") + "\\System32\\drivers\\etc\\hosts" : "/etc/hosts");
        if (!Files.isWritable(hosts)) {
            System.out.println("SKIPPED: no permission to edit " + hosts + " (this check needs administrator/root rights).");
            return;
        }
        Path dir = Files.createTempDirectory("pdprod");
        SecurityChecks.keytool(dir, "-genkeypair", "-alias", "k", "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=api.telegram.org",
                "-ext", "san=dns:api.telegram.org", "-validity", "2", "-storetype", "PKCS12", "-keystore", "tg.p12", "-storepass", "changeit", "-keypass", "changeit");
        SecurityChecks.keytool(dir, "-exportcert", "-alias", "k", "-keystore", "tg.p12", "-storepass", "changeit", "-file", "tg.cer");
        SecurityChecks.keytool(dir, "-importcert", "-noprompt", "-alias", "k", "-file", "tg.cer", "-keystore", "trust.p12", "-storetype", "PKCS12", "-storepass", "changeit");

        Path backup = dir.resolve("hosts.backup");
        Files.copy(hosts, backup, StandardCopyOption.REPLACE_EXISTING);
        int exit;
        try {
            Files.writeString(hosts, Files.readString(backup) + "\n127.0.0.1 api.telegram.org\n", StandardCharsets.UTF_8);
            List<String> cmd = new ArrayList<>();
            cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            cmd.add("-Djavax.net.ssl.trustStore=" + dir.resolve("trust.p12"));
            cmd.add("-Djavax.net.ssl.trustStorePassword=changeit");
            cmd.add("-Dpocketdrop.test.realAddresses=" + dir.resolve("tg.p12"));
            cmd.add("-Djava.net.preferIPv4Stack=true");
            cmd.addAll(List.of("-cp", System.getProperty("java.class.path"), "dev.badr.pocketdrop.AllTests"));
            Process p = new ProcessBuilder(cmd).inheritIO().start();
            exit = p.waitFor(280, TimeUnit.SECONDS) ? p.exitValue() : 1;
        } finally {
            Files.copy(backup, hosts, StandardCopyOption.REPLACE_EXISTING); // always restore
        }
        System.out.println(exit == 0 ? "\nREAL ADDRESSES: all bot tests passed through https://api.telegram.org (host names checked)"
                : "\nREAL ADDRESSES: FAILED");
        System.exit(exit);
    }
}
