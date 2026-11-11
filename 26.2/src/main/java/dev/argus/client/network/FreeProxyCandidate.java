package dev.argus.client.network;

/** One entry from the free proxy mirror, with the metadata used to rank it. */
public record FreeProxyCandidate(
    ProxyEntry.Type type,
    String address,
    int port,
    String countryCode,
    String isp,
    double uptimePercent,
    double latencyMs
) {
    /** Hosting providers, which share datacenter IP reputation. Anything else is treated as residential. */
    private static final String[] HOSTING = {
        "digitalocean", "hetzner", "ovh", "amazon", "aws", "google", "microsoft", "azure", "oracle",
        "alibaba", "tencent", "vultr", "linode", "contabo", "choopa", "leaseweb", "performive",
        "datawagon", "net2atlanta", "secure internet", "psychz", "hostinger", "namecheap", "colocation",
        "m247", "poneytelecom", "scaleway", "ionos", "godaddy", "akamai", "cloudflare", "fastly",
        "netcup", "serverius", "worldstream", "fdcservers", "hivelocity", "quadranet", "interserver",
        "colocrossing", "sharktech", "datacamp", "host", "vps", "server", "cloud", "datacenter"
    };

    public boolean residential() {
        String lower = isp.toLowerCase(java.util.Locale.ROOT);
        for (String hosting : HOSTING) {
            if (lower.contains(hosting)) return false;
        }
        return true;
    }

    public String label() {
        return address + ":" + port;
    }

    /** Converts to a saved proxy entry; returns null if the values are not valid. */
    public ProxyEntry toEntry() {
        try {
            return new ProxyEntry(java.util.UUID.randomUUID().toString(), "", type, address, port, "", "");
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
