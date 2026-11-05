package com.takibo.authorizationserver.infrastructure.jwks;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Plage d'adresses IPv4 ou IPv6 en notation CIDR. Seules les adresses littérales sont
 * acceptées : aucune résolution DNS n'a lieu à la lecture d'une plage.
 */
public final class Cidr {

    private final byte[] network;
    private final int prefixLength;

    private Cidr(byte[] network, int prefixLength) {
        this.network = network;
        this.prefixLength = prefixLength;
    }

    public static Cidr parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CIDR is blank");
        }
        String trimmed = value.trim();
        int slash = trimmed.indexOf('/');
        String address = slash < 0 ? trimmed : trimmed.substring(0, slash);
        byte[] bytes = IpLiterals.parse(address)
                .orElseThrow(() -> new IllegalArgumentException("CIDR address is not an IP literal: " + value))
                .getAddress();
        int maxPrefix = bytes.length * 8;
        int prefix;
        try {
            prefix = slash < 0 ? maxPrefix : Integer.parseInt(trimmed.substring(slash + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("CIDR prefix is not a number: " + value, e);
        }
        if (prefix < 0 || prefix > maxPrefix) {
            throw new IllegalArgumentException("CIDR prefix out of range: " + value);
        }
        return new Cidr(mask(bytes, prefix), prefix);
    }

    public boolean contains(InetAddress address) {
        byte[] candidate = address.getAddress();
        return candidate.length == network.length && Arrays.equals(mask(candidate, prefixLength), network);
    }

    /** Deux plages se chevauchent si la plus large contient le réseau de l'autre. */
    public boolean overlaps(Cidr other) {
        if (network.length != other.network.length) {
            return false;
        }
        Cidr wider = prefixLength <= other.prefixLength ? this : other;
        Cidr narrower = wider == this ? other : this;
        return Arrays.equals(mask(narrower.network, wider.prefixLength), wider.network);
    }

    public boolean isIpv4() {
        return network.length == 4;
    }

    public int prefixLength() {
        return prefixLength;
    }

    private static byte[] mask(byte[] bytes, int prefix) {
        byte[] masked = bytes.clone();
        for (int bit = prefix; bit < masked.length * 8; bit++) {
            masked[bit / 8] &= (byte) ~(0x80 >>> (bit % 8));
        }
        return masked;
    }

    @Override
    public String toString() {
        try {
            return InetAddress.getByAddress(network).getHostAddress() + "/" + prefixLength;
        } catch (UnknownHostException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
