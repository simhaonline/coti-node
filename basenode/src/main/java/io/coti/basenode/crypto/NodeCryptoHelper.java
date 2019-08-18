package io.coti.basenode.crypto;

import io.coti.basenode.data.Hash;
import io.coti.basenode.data.SignatureData;
import org.springframework.stereotype.Service;

import static io.coti.basenode.constants.BaseNodeApplicationConstant.NODE_PRIVATE_KEY;

@Service
public class NodeCryptoHelper {

    private static String NODE_PUBLIC_KEY;
    private static String seed;

    private NodeCryptoHelper() {
        NODE_PUBLIC_KEY = CryptoHelper.getPublicKeyFromPrivateKey(NODE_PRIVATE_KEY);
    }

    public static SignatureData signMessage(byte[] message) {
        return CryptoHelper.signBytes(message, NODE_PRIVATE_KEY);
    }

    public static SignatureData signMessage(byte[] message, Integer index) {
        return CryptoHelper.signBytes(message, CryptoHelper.generatePrivateKey(seed, index).toHexString());
    }

    public Hash generateAddress(String seed, Integer index) {
        if (this.seed == null) {
            this.seed = seed;
        }
        return CryptoHelper.generateAddress(seed, index);
    }

    public static Hash getNodeHash() {
        return new Hash(NODE_PUBLIC_KEY);
    }

    public static Hash getNodeAddress() {
        return CryptoHelper.getAddressFromPrivateKey(NODE_PRIVATE_KEY);
    }
}